package au.gov.amsa.geo.projection;

import java.awt.Point;
import java.awt.geom.Point2D;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import au.gov.amsa.util.navigation.Position;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Uses geotools libraries to perform transformations between coordinate
 * reference systems
 * 
 * @author dxm
 * 
 */
public class Projector {

	private final ProjectorTarget target;
	private final ProjectorBounds bounds;

	private final MathTransform transform;
	private final GeometryFactory geometryFactory;

	public Projector(ProjectorBounds bounds, ProjectorTarget target) {
		this.target = target;
		this.bounds = bounds;
		try {
			transform = CRS.findMathTransform(
					FeatureUtil.getCrs(FeatureUtil.EPSG_4326),
					FeatureUtil.getCrs(bounds.getSrs()));
		} catch (FactoryException e) {
			throw new RuntimeException(e);
		}
		geometryFactory = new GeometryFactory();
	}

	public Point toPoint(double lat, double lon) {
		Point2D point2D = toPoint2D(lat, lon);
		Point p = new Point();
		p.x = (int) Math.round(point2D.getX());
		p.y = (int) Math.round(point2D.getY());
		return p;
	}

	public Point2D.Double toPointInSrs(double lat, double lon) {
		Coordinate coordinate = new Coordinate(lon, lat);
		org.locationtech.jts.geom.Point point = geometryFactory
				.createPoint(coordinate);
		try {
			point = (org.locationtech.jts.geom.Point) JTS.transform(point,
					transform);
			return new Point2D.Double(point.getX(), point.getY());
		} catch (MismatchedDimensionException e) {
			throw new RuntimeException(e);
		} catch (TransformException e) {
			throw new RuntimeException(e);
		}
	}

	public Point2D.Double toPoint2D(double lat, double lon) {
		Coordinate coordinate = new Coordinate(lon, lat);
		org.locationtech.jts.geom.Point point = geometryFactory
				.createPoint(coordinate);
		try {
			point = (org.locationtech.jts.geom.Point) JTS.transform(point,
					transform);
		} catch (MismatchedDimensionException e) {
			throw new RuntimeException(e);
		} catch (TransformException e) {
			throw new RuntimeException(e);
		}

		double proportionX = (point.getX() - bounds.getMinX())
				/ (bounds.getMaxX() - bounds.getMinX());
		double proportionY = (bounds.getMaxY() - point.getY())
				/ (bounds.getMaxY() - bounds.getMinY());
		Point2D.Double point2D = new Point2D.Double(proportionX
				* target.getWidth(), proportionY * target.getHeight());
		return point2D;
	}

	public Position toPosition(double targetX, double targetY) {
		double proportionX = targetX / target.getWidth();
		double proportionY = targetY / target.getHeight();
		double x = proportionX * (bounds.getMaxX() - bounds.getMinX())
				+ bounds.getMinX();
		double y = bounds.getMaxY() - proportionY
				* (bounds.getMaxY() - bounds.getMinY());
		Coordinate coordinate = new Coordinate(x, y);
		org.locationtech.jts.geom.Point point = geometryFactory
				.createPoint(coordinate);
		try {
			point = (org.locationtech.jts.geom.Point) JTS.transform(point,
					transform.inverse());
		} catch (MismatchedDimensionException e) {
			throw new RuntimeException(e);
		} catch (TransformException e) {
			throw new RuntimeException(e);
		}
		return new Position(point.getY(), point.getX());
	}

	@Override
	public String toString() {
		return "ProjectorImpl [target=" + target + ", bounds=" + bounds + "]";
	}

}
