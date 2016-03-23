/*
 * Copyright (c) 2016 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 */

package gov.nasa.worldwind;

import android.view.MotionEvent;

import gov.nasa.worldwind.geom.Location;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.gesture.GestureListener;
import gov.nasa.worldwind.gesture.GestureRecognizer;
import gov.nasa.worldwind.gesture.PanRecognizer;
import gov.nasa.worldwind.gesture.PinchRecognizer;
import gov.nasa.worldwind.gesture.RotationRecognizer;
import gov.nasa.worldwind.globe.Globe;
import gov.nasa.worldwind.util.WWMath;

public class BasicNavigatorController implements NavigatorController {

    protected WorldWindow wwd;

    protected float lastX;

    protected float lastY;

    protected double beginAltitude;

    protected double beginHeading;

    protected double minAltitude = 10;

    protected PanRecognizer panRecognizer = new PanRecognizer();

    protected GestureRecognizer pinchRecognizer = new PinchRecognizer();

    protected GestureRecognizer rotationRecognizer = new RotationRecognizer();

    public BasicNavigatorController() {

        this.panRecognizer.setMaxNumberOfPointers(2);
        this.panRecognizer.addListener(new GestureListener() {
            @Override
            public void gestureStateChanged(MotionEvent event, GestureRecognizer recognizer) {
                handlePan(recognizer);
            }
        });

        this.pinchRecognizer.addListener(new GestureListener() {
            @Override
            public void gestureStateChanged(MotionEvent event, GestureRecognizer recognizer) {
                handlePinch(recognizer);
            }
        });

        this.rotationRecognizer.addListener(new GestureListener() {
            @Override
            public void gestureStateChanged(MotionEvent event, GestureRecognizer recognizer) {
                handleRotate(recognizer);
            }
        });
    }

    @Override
    public WorldWindow getWorldWindow() {
        return wwd;
    }

    @Override
    public void setWorldWindow(WorldWindow wwd) {
        if (this.wwd != null) {
            this.wwd.removeGestureRecognizer(this.panRecognizer);
            this.wwd.removeGestureRecognizer(this.pinchRecognizer);
            this.wwd.removeGestureRecognizer(this.rotationRecognizer);
        }

        this.wwd = wwd;

        if (this.wwd != null) {
            this.wwd.addGestureRecognizer(this.panRecognizer);
            this.wwd.addGestureRecognizer(this.pinchRecognizer);
            this.wwd.addGestureRecognizer(this.rotationRecognizer);
        }
    }

    protected void handlePan(GestureRecognizer recognizer) {
        int state = recognizer.getState();
        float dx = recognizer.getTranslationX();
        float dy = recognizer.getTranslationY();
        Navigator navigator = this.wwd.getNavigator();

        if (state == WorldWind.BEGAN) {
            this.lastX = 0;
            this.lastY = 0;
        } else if (state == WorldWind.CHANGED) {
            // Convert the translation from screen coordinates to degrees. Use this navigator's altitude as a metric for
            // converting screen pixels to meters, and use the globe's radius for converting from meters to arc degrees.
            Position pos = navigator.getPosition();
            Globe globe = this.wwd.getGlobe();
            double globeRadius = globe.getRadiusAt(pos.latitude, pos.longitude);
            double metersPerPixel = this.wwd.pixelSizeAtDistance(pos.altitude);
            double forwardMeters = (dy - this.lastY) * metersPerPixel;
            double sideMeters = -(dx - this.lastX) * metersPerPixel;
            double forwardDegrees = Math.toDegrees(forwardMeters / globeRadius);
            double sideDegrees = Math.toDegrees(sideMeters / globeRadius);

            // Apply the change in latitude and longitude to this navigator, relative to the current heading.
            double headingDegrees = navigator.getHeading();
            double headingRadians = Math.toRadians(headingDegrees);
            double sinHeading = Math.sin(headingRadians);
            double cosHeading = Math.cos(headingRadians);
            double latDegrees = pos.latitude + forwardDegrees * cosHeading - sideDegrees * sinHeading;
            double lonDegrees = pos.longitude + forwardDegrees * sinHeading + sideDegrees * cosHeading;

            // If the navigator has panned over either pole, compensate by adjusting the longitude and heading to move
            // the navigator to the appropriate spot on the other side of the pole.
            if (latDegrees < -90 || latDegrees > 90) {
                latDegrees = Location.normalizeLatitude(latDegrees);
                lonDegrees = Location.normalizeLongitude(lonDegrees + 180);
                headingDegrees = WWMath.normalizeAngle(headingDegrees + 180);
            } else if (lonDegrees < -180 || lonDegrees > 180) {
                lonDegrees = Location.normalizeLongitude(lonDegrees);
            }

            navigator.getPosition().set(latDegrees, lonDegrees);
            navigator.setHeading(headingDegrees);
            this.wwd.requestRender();

            this.lastX = dx;
            this.lastY = dy;
        }
    }

    protected void handlePinch(GestureRecognizer recognizer) {
        int state = recognizer.getState();
        float scale = ((PinchRecognizer) recognizer).getScale();
        Navigator navigator = this.wwd.getNavigator();

        if (state == WorldWind.BEGAN) {
            this.beginAltitude = navigator.getPosition().altitude;
        } else if (state == WorldWind.CHANGED) {
            if (scale != 0) {
                // Apply the change in pinch scale to this navigator's altitude, relative to the altitude when the
                // gesture began.
                double altitude = this.beginAltitude / scale;
                double minAltitude = this.minAltitude;
                double maxAltitude = this.wwd.distanceToViewGlobeExtents() * 2;
                altitude = WWMath.clamp(altitude, minAltitude, maxAltitude);
                navigator.getPosition().altitude = altitude;
                this.wwd.requestRender();
            }
        }
    }

    protected void handleRotate(GestureRecognizer recognizer) {
        int state = recognizer.getState();
        float rotation = ((RotationRecognizer) recognizer).getRotation();
        Navigator navigator = this.wwd.getNavigator();

        if (state == WorldWind.BEGAN) {
            this.beginHeading = navigator.getHeading();
        } else if (state == WorldWind.CHANGED) {
            // Apply the change in gesture rotation to this navigator's current heading, relative to the heading when
            // the gesture began.
            double newHeading = WWMath.normalizeDegrees(this.beginHeading - rotation);
            navigator.setHeading(newHeading);
            this.wwd.requestRender();
        }
    }
}
