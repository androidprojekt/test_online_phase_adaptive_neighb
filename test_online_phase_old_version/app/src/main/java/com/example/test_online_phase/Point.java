package com.example.test_online_phase;

public class Point {

    private int x,y;
    private double euclideanDistance;

    public Point(int x, int y, double euclideanDistance) {
        this.x = x;
        this.y = y;
        this.euclideanDistance = euclideanDistance;
    }

    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }
    public double getEuclideanDistance() {
        return euclideanDistance;
    }
}

