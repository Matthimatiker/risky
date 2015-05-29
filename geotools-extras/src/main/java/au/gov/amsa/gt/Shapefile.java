package au.gov.amsa.gt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;

public final class Shapefile {

	private final DataStore datastore;
	private final static GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
	// 0 = not loaded, not closed, 1 = loaded, 2 = closed
	private final AtomicInteger state = new AtomicInteger(0);
	private static final int NOT_LOADED = 0;
	private static final int LOADED = 1;
	private static final int CLOSED = 2;

	private volatile List<PreparedGeometry> geometries;
	private final double bufferDistance;

	private Shapefile(File file, double bufferDistance) {
		this.bufferDistance = bufferDistance;
		try {
			Map<String, Serializable> map = new HashMap<>();
			map.put("url", file.toURI().toURL());
			datastore = DataStoreFinder.getDataStore(map);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Shapefile from(File file) {
		return from(file, 0);
	}

	public static Shapefile from(File file, double bufferDistance) {
		return new Shapefile(file, bufferDistance);
	}

	public static void createPolygon(List<Coordinate> coords, File output) {
		ShapefileCreator.createPolygon(coords, output);
	}

	public static Shapefile fromZip(InputStream is) {
		return fromZip(is, 0);
	}

	public static Shapefile fromZip(InputStream is, double bufferDistance) {
		try {
			File directory = Files.createTempDirectory("shape-").toFile();
			ZipUtil.unzip(is, directory);
			return new Shapefile(directory, bufferDistance);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Shapefile load() {
		if (state.compareAndSet(NOT_LOADED, LOADED)) {
			try {
				final List<PreparedGeometry> geometries = new ArrayList<>();
				for (String typeName : datastore.getTypeNames()) {
					SimpleFeatureSource source = datastore
							.getFeatureSource(typeName);
					final SimpleFeatureCollection features = source
							.getFeatures();
					SimpleFeatureIterator it = features.features();
					while (it.hasNext()) {
						SimpleFeature feature = it.next();
						Geometry g = (Geometry) feature.getDefaultGeometry();
						if (bufferDistance > 0)
							g = g.buffer(bufferDistance);
						geometries.add(PreparedGeometryFactory.prepare(g));
					}
					it.close();
				}
				this.geometries = geometries;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else if (state.get() == CLOSED)
			throw new RuntimeException(
					"Shapefile is closed and can't be accessed");
		return this;
	}

	public List<PreparedGeometry> geometries() {
		load();
		return geometries;
	}

	public boolean contains(double lat, double lon) {
		load();
		return GeometryUtil.contains(GEOMETRY_FACTORY, geometries, lat, lon);
	}

	public void close() {
		if (state.compareAndSet(NOT_LOADED, CLOSED)
				|| state.compareAndSet(LOADED, CLOSED))
			datastore.dispose();
	}

}
