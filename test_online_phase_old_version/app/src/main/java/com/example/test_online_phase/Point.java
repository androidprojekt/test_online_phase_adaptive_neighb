package com.example.test_online_phase;

public class Point {

    int x,y;
    double euclideanDistance;

    public Point(int x, int y, double euclideanDistance) {
        this.x = x;
        this.y = y;
        this.euclideanDistance = euclideanDistance;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public double getEuclideanDistance() {
        return euclideanDistance;
    }

    public void setEuclideanDistance(double euclideanDistance) {
        this.euclideanDistance = euclideanDistance;
    }
}
