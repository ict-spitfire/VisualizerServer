package eu.spitfire_project;
public class TPoint {
    public double x, y;

    public TPoint() {
        x = 0;
        y = 0;
    }

    public TPoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public TPoint(int x, int y) {
        this.x = (double)x;
        this.y = (double)y;
    }

    public static double dist(TPoint p1, TPoint p2) {
        double d = Math.pow(p1.x-p2.x, 2) + Math.pow(p1.y-p2.y, 2);
        return Math.sqrt(d);
    }
}
