package au.gov.amsa.ais;

import au.gov.amsa.util.SixBit;

/**
 * Utility class for extracting parts of an ais message as unsigned integers,
 * signed integers or strings.
 * 
 * @author dxm
 * 
 */
public class AisExtractor {

	// private final String decodedMessage;

	private final boolean[] bitSet;

	/**
	 * Constructor. Does not do a minimum length check.
	 * 
	 * @param message
	 */
	public AisExtractor(String message, int padBits) {
		this(message, null, padBits);
	}

	/**
	 * Constructor. If message once decoded is less than minLength then throws
	 * {@link AisParseException}.
	 * 
	 * @param message
	 * @param minLength
	 */
	public AisExtractor(String message, Integer minLength, int padBits) {
		this.bitSet = SixBit.sixBitToBits(message, padBits);
		// this.decodedMessage = Util.decodeMessage(message);
		if (minLength != null && bitSet.length < minLength) {
			throw new AisParseException(AisParseException.NOT_CONSISTENT_DECODED_STRING
			        + ", length was " + bitSet.length + " and should be >=" + minLength);
		}
	}

	/**
	 * Returns the message id field (the first 6 characters of the decoded
	 * message).
	 * 
	 * @return
	 */
	public int getMessageId() {
		return getValue(0, 6);
	}

	/**
	 * Returns an unsigned integer value using the bits from character position
	 * start to position stop in the decoded message.
	 * 
	 * @param start
	 * @param stop
	 * @return
	 */
	public int getValue(int start, int stop) {
		return (int) SixBit.getValue(start, stop, bitSet);
	}

	/**
	 * Returns a signed integer value using the bits from character position
	 * start to position stop in the decoded message.
	 * 
	 * @param start
	 * @param stop
	 * @return
	 */
	public int getSignedValue(int start, int stop) {
		return (int) SixBit.getSignedValue(start, stop, bitSet);
	}

	// /**
	// * Returns the characters from position start to position stop in the
	// * decoded message.
	// *
	// * @param start
	// * @param stop
	// * @return
	// */
	// public String getStringOld(int start, int stop) {
	// try {
	// return Util.getAsciiStringFrom6BitStr(decodedMessage.substring(start,
	// stop));
	// } catch (RuntimeException e) {
	// throw new AisParseException(e);
	// }
	// }

	public String getString(int from, int to) {
		return SixBit.getString(from, to, bitSet);
	}

	// /**
	// * Returns an integer value using the bits from character position start
	// to
	// * position stop in the decoded message. The returned value is a signed
	// * integer if the parameter <code>signed</code> is true otherwise the
	// * returned value is an unsigned integer.
	// *
	// * @param start
	// * @param stop
	// * @param signed
	// * @return
	// */
	// public int getValue(int start, int stop, boolean signed) {
	// try {
	// return Util.getValueByBinStr(decodedMessage.substring(start, stop),
	// signed);
	// } catch (RuntimeException e) {
	// throw new AisParseException(e);
	// }
	// }

}
