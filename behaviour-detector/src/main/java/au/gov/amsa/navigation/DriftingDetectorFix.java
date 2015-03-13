package au.gov.amsa.navigation;

import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

import rx.Observable;
import rx.Observable.Operator;
import rx.Observable.Transformer;
import rx.Subscriber;
import rx.functions.Func1;
import au.gov.amsa.risky.format.Fix;
import au.gov.amsa.risky.format.NavigationalStatus;
import au.gov.amsa.util.RingBuffer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class DriftingDetectorFix {

	static final double KNOTS_TO_METRES_PER_SECOND = 0.5144444;
	@VisibleForTesting
	static final int HEADING_COG_DIFFERENCE_MIN = 70;
	@VisibleForTesting
	static final int HEADING_COG_DIFFERENCE_MAX = 110;
	@VisibleForTesting
	static final double MAX_DRIFTING_SPEED_KNOTS = 4;
	@VisibleForTesting
	static final double MIN_DRIFTING_SPEED_KNOTS = 0.3;

	private static final long WINDOW_SIZE_MS = 5 * 60 * 1000;
	private static final double MIN_PROPORTION = 0.5;
	private static final double NON_DRIFTING_THRESHOLD_MS = 5 * 60 * 1000;

	public Observable<DriftCandidate> getCandidates(Observable<Fix> o) {
		return o.lift(detectDriftCandidates());
	}

	private static class FixAndStatus {
		final Fix fix;
		final boolean drifting;

		public FixAndStatus(Fix fix, boolean drifting) {
			this.fix = fix;
			this.drifting = drifting;
		}

	}

	/**
	 * This operator expects a stream of fixes of increasing time except when
	 * the mmsi changes (it can!).
	 * 
	 * @return an operator to detect drift candidates
	 */
	private static Operator<DriftCandidate, Fix> detectDriftCandidates() {
		return new Operator<DriftCandidate, Fix>() {

			@Override
			public Subscriber<? super Fix> call(final Subscriber<? super DriftCandidate> child) {
				return new Subscriber<Fix>(child) {
					final int SIZE = 1000;
					final AtomicLong driftingSinceTime = new AtomicLong(Long.MAX_VALUE);
					final AtomicLong nonDriftingSinceTime = new AtomicLong(Long.MAX_VALUE);
					final AtomicLong currentMmsi = new AtomicLong(-1);
					final RingBuffer<FixAndStatus> q = RingBuffer.create(SIZE);

					@Override
					public void onCompleted() {
						child.onCompleted();
					}

					@Override
					public void onError(Throwable e) {
						child.onError(e);
					}

					@Override
					public void onNext(Fix f) {
						handleFix(f, q, child, driftingSinceTime, nonDriftingSinceTime, currentMmsi);
					}

				};
			}
		};
	}

	static void handleFix(Fix f, RingBuffer<FixAndStatus> q,
	        Subscriber<? super DriftCandidate> child, AtomicLong driftingSinceTime,
	        AtomicLong nonDriftingSinceTime, AtomicLong currentMmsi) {
		// when a fix arrives that is a drift detection start building a queue
		// of fixes. If a certain proportion of fixes are drift detection with a
		// minimum window of report time from the first detection report time
		// then report them to the child subscriber
		if (currentMmsi.get() != f.getMmsi() || q.size() == q.maxSize()) {
			// note that hitting maxSize in q should only happen for rubbish
			// mmsi codes like 0 so we are happy to clear the q
			q.clear();
			driftingSinceTime.set(Long.MAX_VALUE);
			nonDriftingSinceTime.set(Long.MAX_VALUE);
			currentMmsi.set(f.getMmsi());
		}
		if (q.isEmpty()) {
			if (IS_CANDIDATE.call(f)) {
				q.push(new FixAndStatus(f, true));
				// reset non drifting time because drifting detected
				nonDriftingSinceTime.set(Long.MAX_VALUE);
				// if drifting since not set then use this fix time
				driftingSinceTime.compareAndSet(Long.MAX_VALUE, f.getTime());
			} else {
				// if non drifting start time not set then use this fix
				nonDriftingSinceTime.compareAndSet(Long.MAX_VALUE, f.getTime());
				return;
			}
		} else {
			// queue is non-empty so add to the queue
			q.push(new FixAndStatus(f, IS_CANDIDATE.call(f)));
		}

		// process the queue if time interval long enough
		if (f.getTime() - q.peek().fix.getTime() >= WINDOW_SIZE_MS) {
			// count the number of candidates
			int count = countDrifting(q);
			// if a decent number of drift candidates found in the time interval
			// then emit them
			if ((double) count / q.size() >= MIN_PROPORTION) {
				emitDriftersAndUpdateTimes(q, child, driftingSinceTime, nonDriftingSinceTime);
			}
		}

	}

	private static void emitDriftersAndUpdateTimes(RingBuffer<FixAndStatus> q,
	        Subscriber<? super DriftCandidate> child, AtomicLong driftingSinceTime,
	        AtomicLong nonDriftingSinceTime) {
		Enumeration<FixAndStatus> en = q.values();
		while (en.hasMoreElements()) {
			FixAndStatus x = en.nextElement();
			q.pop();
			if (x.drifting) {
				// emit DriftCandidate with driftingSinceTime
				long driftingSince;
				if (x.fix.getTime() - nonDriftingSinceTime.get() > NON_DRIFTING_THRESHOLD_MS)
					driftingSince = x.fix.getTime();
				else
					driftingSince = driftingSinceTime.get();
				child.onNext(new DriftCandidate(x.fix, driftingSince));
				nonDriftingSinceTime.set(Long.MAX_VALUE);
			} else {
				nonDriftingSinceTime.compareAndSet(Long.MAX_VALUE, x.fix.getTime());
			}
		}
	}

	private static int countDrifting(RingBuffer<FixAndStatus> q) {
		int count = 0;
		Enumeration<FixAndStatus> en = q.values();
		while (en.hasMoreElements()) {
			if (en.nextElement().drifting)
				count++;
		}
		return count;
	}

	public static DriftingTransformer detectDrift() {
		return new DriftingTransformer();
	}

	private static class DriftingTransformer implements Transformer<Fix, DriftCandidate> {

		private final DriftingDetectorFix d = new DriftingDetectorFix();

		@Override
		public Observable<DriftCandidate> call(Observable<Fix> o) {
			return d.getCandidates(o);
		}
	}

	@VisibleForTesting
	static Func1<Fix, Boolean> IS_CANDIDATE = new Func1<Fix, Boolean>() {

		@Override
		public Boolean call(Fix p) {
			if (p.getCourseOverGroundDegrees().isPresent()
			        && p.getHeadingDegrees().isPresent()
			        && p.getSpeedOverGroundKnots().isPresent()
			        && (!p.getNavigationalStatus().isPresent() || (p.getNavigationalStatus().get() != NavigationalStatus.AT_ANCHOR && p
			                .getNavigationalStatus().get() != NavigationalStatus.MOORED))) {
				double diff = diff(p.getCourseOverGroundDegrees().get(), p.getHeadingDegrees()
				        .get());
				return diff >= HEADING_COG_DIFFERENCE_MIN && diff <= HEADING_COG_DIFFERENCE_MAX
				        && p.getSpeedOverGroundKnots().get() <= MAX_DRIFTING_SPEED_KNOTS

				        && p.getSpeedOverGroundKnots().get() > MIN_DRIFTING_SPEED_KNOTS;
			} else
				return false;
		}
	};

	static double diff(double a, double b) {
		Preconditions.checkArgument(a >= 0 && a < 360);
		Preconditions.checkArgument(b >= 0 && b < 360);
		double value;
		if (a < b)
			value = a + 360 - b;
		else
			value = a - b;
		if (value > 180)
			return 360 - value;
		else
			return value;

	};

}