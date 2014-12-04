package au.gov.amsa.util.nmea;

import java.io.File;

public interface FileFactory {

	File file(String line, long arrivalTime);

	String key(String line, long arrivalTime);
}
