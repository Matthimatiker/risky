package au.gov.amsa.risky.format;

import static au.gov.amsa.risky.format.BinaryFixes.BINARY_FIX_BYTES;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.zip.GZIPInputStream;

import com.github.davidmoten.util.Optional;

import au.gov.amsa.risky.format.BinaryFixesOnSubscribeWithBackp.State;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observables.AbstractOnSubscribe;

public class BinaryFixesOnSubscribeWithBackp extends AbstractOnSubscribe<Fix, State> {

    private final InputStream is;
    private final Optional<Integer> mmsi;

    public BinaryFixesOnSubscribeWithBackp(InputStream is, Optional<Integer> mmsi) {
        this.is = is;
        this.mmsi = mmsi;
    }

    public static class State {
        final InputStream is;
        final Optional<Integer> mmsi;
        final Queue<Fix> queue;

        public State(InputStream is, Optional<Integer> mmsi, Queue<Fix> queue) {
            this.is = is;
            this.mmsi = mmsi;
            this.queue = queue;
        }

    }

    /**
     * Returns stream of fixes from the given file. If the file name ends in
     * '.gz' then the file is unzipped before being read.
     * 
     * @param file
     * @return fixes stream
     */
    public static Observable<Fix> from(final File file, boolean recordIncludesMmsi) {

        Func0<InputStream> resourceFactory = new Func0<InputStream>() {

            @Override
            public InputStream call() {
                try {
                    if (file.getName().endsWith(".gz"))
                        return new GZIPInputStream(new FileInputStream(file));
                    else
                        return new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Func1<InputStream, Observable<Fix>> obsFactory = new Func1<InputStream, Observable<Fix>>() {

            @Override
            public Observable<Fix> call(InputStream is) {
                Optional<Integer> mmsi;
                if (recordIncludesMmsi)
                    mmsi = Optional.absent();
                else
                    mmsi = Optional.of(BinaryFixesUtil.getMmsi(file));

                return Observable.create(new BinaryFixesOnSubscribeWithBackp(is, mmsi));
            }
        };
        Action1<InputStream> disposeAction = new Action1<InputStream>() {

            @Override
            public void call(InputStream is) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return Observable.using(resourceFactory, obsFactory, disposeAction, true);

    }

    @Override
    protected State onSubscribe(Subscriber<? super Fix> subscriber) {
        return new State(is, mmsi, new LinkedList<Fix>());
    }

    @Override
    protected void next(rx.observables.AbstractOnSubscribe.SubscriptionState<Fix, State> state) {
        Fix f = state.state().queue.poll();
        if (f != null)
            state.onNext(f);
        else {
            byte[] bytes = new byte[4096 * BINARY_FIX_BYTES];
            int length;
            try {
                if ((length = state.state().is.read(bytes)) > 0) {
                    for (int i = 0; i < length; i += BINARY_FIX_BYTES) {
                        ByteBuffer bb = ByteBuffer.wrap(bytes, i, BINARY_FIX_BYTES);
                        final int mmsi;
                        if (state.state().mmsi.isPresent()) {
                            mmsi = state.state().mmsi.get();
                        } else {
                            mmsi = bb.getInt();
                        }
                        Fix fix = BinaryFixesUtil.toFix(mmsi, bb);
                        state.state().queue.add(fix);
                    }
                    state.onNext(state.state().queue.remove());
                } else
                    state.onCompleted();
            } catch (IOException e) {
                state.onError(e);
            }
        }

    }

}
