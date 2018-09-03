package com.example.patrick.kingdominoscorer;

import android.content.Context;
import android.icu.util.TimeUnit;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.AgastFeatureDetector;
import org.opencv.features2d.BOWImgDescriptorExtractor;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FastFeatureDetector;
import org.opencv.features2d.Feature2D;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.lang.Runnable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;

public class FrameAnalyzer implements Runnable {
    FrameRequest request;
    private static final String TAG = "FrameAnalyzer";
    Mat template;
    Mat orig;
    Mat rot90;
    Mat rot180;
    Mat rot270;

    public FrameAnalyzer(Context context, FrameRequest request) {
        this.request = request;
        template = new Mat();
        try {
            template = Utils.loadResource(context, R.drawable.plainscoast, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
        } catch (java.io.IOException e) {
            Log.i(TAG, "failed to load the template LUL");
        }

        //Imgproc.blur(orig, template, new Size(3,3));

        if (template != null) {
            Log.i(TAG, "Loaded template with height: " + template.height() + " width: " + template.width());
        } else {
            Log.i(TAG, "Failed to load template");
        }
        rot90 = new Mat();
        rot180 = new Mat();
        rot270 = new Mat();
    }

    private void getIntersection(Line a, Line b, Point p) {
        double d = ((a.p1.x - a.p2.x) * (b.p1.y - b.p2.y)) - ((a.p1.y - a.p2.y) * (b.p1.x - b.p2.x));
        double thresh = 50;
        if (d > 0) {
            p.x = (((a.p1.x * a.p2.y) - (a.p1.y * a.p2.x)) * (b.p1.x - b.p2.x) - (a.p1.x - a.p2.x) * ((b.p1.x * b.p2.y) - (b.p1.y * b.p2.x))) / d;
            p.y = (((a.p1.x * a.p2.y) - (a.p1.y * a.p2.x)) * (b.p1.y - b.p2.y) - (a.p1.y - a.p2.y) * ((b.p1.x * b.p2.y) - (b.p1.y * b.p2.x))) / d;
            if (p.x < Math.min(a.p1.x, a.p2.x) - thresh || p.x > Math.max(a.p1.x, a.p2.x) + thresh || p.y < Math.min(a.p1.y, a.p2.y) - thresh || p.y > Math.max(a.p1.y, a.p2.y) ||
                    p.x < Math.min(b.p1.x, b.p2.x) - thresh || p.x > Math.max(b.p1.x, b.p2.x) + thresh || p.y < Math.min(b.p1.y, b.p2.y) - thresh || p.y > Math.max(b.p1.y, b.p2.y)) {
                p.x = -1;
                p.y = -1;
            }
        } else {
            p.x = -1;
            p.y = -1;
        }
    }

    public void sortCorners(ArrayList<Point> corners, Point center) {
        ArrayList<Point> top = new ArrayList<>();
        ArrayList<Point> bottom = new ArrayList<>();
        for (int i = 0; i < corners.size(); ++i) {
            if (corners.get(i).y < center.y) {
                top.add(corners.get(i));
            } else {
                bottom.add(corners.get(i));
            }
        }
        if (top.size() == 0 || bottom.size() == 0) {
            Log.i(TAG, "center: " + center.x + " " + center.y);
            for (int i = 0; i < corners.size(); ++i) {
                Log.i(TAG, "i: " + i + " " + corners.get(i).x + " " + corners.get(i).y);
            }
        }
        Collections.sort(top, new SortByX());
        Collections.sort(bottom, new SortByX());
        corners.clear();
        corners.add(top.get(0));
        corners.add(top.get(top.size() - 1));
        corners.add(bottom.get(0));
        corners.add(bottom.get(bottom.size() - 1));
        top.clear();
        bottom.clear();
    }

    public boolean findMatch(Mat frame) {
        Mat bright = new Mat();
        Mat gray = new Mat();
        Mat frameMask = new Mat(new Size(frame.cols(), frame.rows()), CvType.CV_8UC1, Scalar.all(255));
        Mat templateMask = new Mat(new Size(template.cols(), template.rows()), CvType.CV_8UC1, Scalar.all(255));

        /* Detect keypoints */
        MatOfKeyPoint frameKeypoints = new MatOfKeyPoint();
        MatOfKeyPoint templateKeypoints = new MatOfKeyPoint();
        Mat frameDescriptors = new Mat();
        Mat templateDescriptors = new Mat();
        //frame.convertTo(bright, -1, 1, -(int)request.linesThreshold);
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
        //Imgproc.blur(bright, gray, new Size(3,3));
        //FastFeatureDetector detector = FastFeatureDetector.create(400, true, FastFeatureDetector.FAST_N);
        //AgastFeatureDetector detector = AgastFeatureDetector.create(400, true, AgastFeatureDetector.THRESHOLD);

        ORB detector = ORB.create(500, 1.2f, 16, 15, 0, 2, ORB.FAST_SCORE, 31, 20);
        //ORB detector = ORB.create();

        detector.detectAndCompute(gray, frameMask, frameKeypoints, frameDescriptors);
        detector.detectAndCompute(template, templateMask, templateKeypoints, templateDescriptors);

        Log.i(TAG, "frame size: " + gray.cols() + " x " + gray.rows());
        Log.i(TAG, "frame keypoints: " + frameKeypoints.toArray().length);
        Log.i(TAG, "template keypoints: " + templateKeypoints.toArray().length);

        gray.release();
        bright.release();
        frameMask.release();
        templateMask.release();

        if (frameKeypoints.toArray().length == 0 || templateKeypoints.toArray().length == 0 || frameDescriptors.empty() || templateDescriptors.empty()) {
            frameKeypoints.release();
            templateKeypoints.release();
            frameDescriptors.release();
            templateDescriptors.release();
            return false;
        }

        Log.i(TAG, "frame descriptors: " + frameDescriptors.type());
        Log.i(TAG, "template descriptors: " + templateDescriptors.type());

        /* Match descriptor vectors. */
        MatOfDMatch matches = new MatOfDMatch();
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
        matcher.match(templateDescriptors, frameDescriptors, matches);

        Log.i(TAG, "matches: " + matches.toArray().length);

        /* Calculate max and min distances between keypoints. */
        double maxDist = 0;
        double minDist = 100;
        for (int i = 0; i < templateDescriptors.rows(); ++i) {
            double dist = matches.toArray()[i].distance;
            if (dist < minDist) minDist = dist;
            if (dist > maxDist) maxDist = dist;
        }

        /* Select only the good matches. */
        ArrayList<DMatch> goodMatches = new ArrayList<>();
        for (int i = 0; i < templateDescriptors.rows(); ++i) {
            DMatch dm = matches.toArray()[i];
            if (dm.distance < 3 * minDist) goodMatches.add(dm);
        }

        /* Get the keypoints from the good matches. */
        ArrayList<Point> templatePoints = new ArrayList<>();
        ArrayList<Point> framePoints = new ArrayList<>();
        for (int i = 0; i < goodMatches.size(); ++i) {
            templatePoints.add(templateKeypoints.toArray()[goodMatches.get(i).queryIdx].pt);
            framePoints.add(frameKeypoints.toArray()[goodMatches.get(i).trainIdx].pt);
        }

        frameKeypoints.release();
        templateKeypoints.release();
        matches.release();
        goodMatches.clear();

        if (templatePoints.size() == 0 || framePoints.size() == 0) {
            templatePoints.clear();
            framePoints.clear();
            return false;
        }

        Log.i(TAG, "template points: " + templatePoints.size());
        Log.i(TAG, "frame points: " + framePoints.size());

        MatOfPoint2f templateMat = new MatOfPoint2f();
        MatOfPoint2f frameMat = new MatOfPoint2f();
        templateMat.fromList(templatePoints);
        frameMat.fromList(framePoints);
        Mat H = Calib3d.findHomography(templateMat, frameMat);

        if (H.empty()) {
            templatePoints.clear();
            framePoints.clear();
            return false;
        }

        /* Get the corners from the template */
        Point templateCorners[] = new Point[4];
        templateCorners[0] = new Point(0, 0);
        templateCorners[1] = new Point(template.cols(), 0);
        templateCorners[2] = new Point(template.cols(), template.rows());
        templateCorners[3] = new Point(0, template.rows());
        MatOfPoint2f frameCornerMat = new MatOfPoint2f();
        Core.perspectiveTransform(new MatOfPoint2f(templateCorners), frameCornerMat, H);

        Point frameCorners[] = frameCornerMat.toArray();
        Log.i(TAG, "Frame corners: " + frameCorners.length);
        request.lines.add(new Line(new Point(frameCorners[0].x + template.cols(), frameCorners[0].y), new Point(frameCorners[1].x + template.cols(), frameCorners[1].y)));
        request.lines.add(new Line(new Point(frameCorners[1].x + template.cols(), frameCorners[1].y), new Point(frameCorners[2].x + template.cols(), frameCorners[2].y)));
        request.lines.add(new Line(new Point(frameCorners[2].x + template.cols(), frameCorners[2].y), new Point(frameCorners[3].x + template.cols(), frameCorners[3].y)));
        request.lines.add(new Line(new Point(frameCorners[3].x + template.cols(), frameCorners[3].y), new Point(frameCorners[0].x + template.cols(), frameCorners[0].y)));

        templatePoints.clear();
        framePoints.clear();
        frameCornerMat.release();
        return true;
    }

    public boolean detectMatch(Mat frame, Mat template, Match match) {
        double threshold = request.cannyThreshold;
        Mat result = new Mat();
        Imgproc.matchTemplate(frame, template, result, Imgproc.TM_CCORR_NORMED);
        Core.MinMaxLocResult mmlr = Core.minMaxLoc(result);
        if (mmlr.maxVal > threshold) {
            match.topLeft = new Point(mmlr.maxLoc.x, mmlr.maxLoc.y);
            match.size = new Size(template.cols(), template.rows());
            return true;
        }
        result.release();
        return false;
    }

    public Match matchTile(Mat frame, Mat tile) {
        double resizeStep = .8;
        Match match = new Match();
        Size curSize = new Size(tile.cols(), tile.rows());
        Mat resized = new Mat();
        Mat rotated = new Mat();
        Point p = new Point();

        Core.rotate(frame, rot90, 0);
        Core.rotate(frame, rot180, 1);
        Core.rotate(frame, rot270, 2);

        while (curSize.width > frame.cols() && curSize.height > frame.height()) {
            curSize.width *= resizeStep;
            curSize.height *= resizeStep;
        }

        while (curSize.height > 1 && curSize.width > 1) {
            Imgproc.resize(tile, resized, curSize);
            if (detectMatch(frame, resized, match)) {
                match.rotation = 0;
                break;
            } else if (detectMatch(rot90, resized, match)) {
                match.rotation = 1;
                p.x = match.topLeft.y;
                p.y = rot90.width() - match.topLeft.x;
                match.topLeft.x = p.x;
                match.topLeft.y = p.y - match.size.width;
                match.size = new Size(match.size.height, match.size.width);
                break;
            } else if (detectMatch(rot180, resized, match)) {
                match.rotation = 2;
                p.x = rot180.width() - match.topLeft.x;
                p.y = rot180.height() - match.topLeft.y;
                match.topLeft.x = p.x - match.size.width;
                match.topLeft.y = p.y - match.size.height;
                break;
            } else if (detectMatch(rot270, resized, match)) {
                match.rotation = 3;
                p.x = rot270.height() - match.topLeft.y;
                p.y = match.topLeft.x;
                match.topLeft.x = p.x - match.size.height;
                match.topLeft.y = p.y;
                match.size = new Size(match.size.height, match.size.width);
                break;
            }

            curSize.width *= resizeStep;
            curSize.height *= resizeStep;
        }
        resized.release();
        rotated.release();
        return match;
    }

    public void run() {
        if (template == null) {
            Log.i(TAG, "failed to load template");
        } else {
            Log.i(TAG, "Loaded template with height: " + template.height() + " width: " + template.width());
        }

        while (true) {
            while (request.state == FrameRequest.State.COMPLETED || request.state == FrameRequest.State.INVALID);
            findMatch(request.frame);
            request.state = FrameRequest.State.COMPLETED;
            /*
            Mat gray = new Mat();
            Imgproc.cvtColor(request.frame, gray, Imgproc.COLOR_BGR2GRAY);

            request.match = matchTile(gray, template);
            if (request.match.topLeft != null) {
                ArrayList<Point> rects = new ArrayList<>();
                rects.add(new Point(request.match.topLeft.x, request.match.topLeft.y));
                rects.add(new Point(request.match.topLeft.x + request.match.size.width, request.match.topLeft.y + request.match.size.height));
                request.rects.add(rects);
            }

            gray.release();
            request.state = FrameRequest.State.COMPLETED;*/
        }
    }

    /*
    public void run() {
        ArrayList<Line> lineList = new ArrayList<>();
        ArrayList<ArrayList<Point>> corners = new ArrayList<>();
        while (true) {
            while (!request.sent);
            Mat gray = new Mat();
            Mat edges = new Mat();
            Mat lines = new Mat();
            //Log.i(TAG, "w: " + request.frame.width() + " h: " + request.frame.height());
            Imgproc.cvtColor(request.frame, gray, Imgproc.COLOR_BGR2GRAY);
            //Log.i(TAG, "w: " + gray.width() + " h: " + gray.height());
            Imgproc.blur(gray, edges, new Size(3, 3));
            if (request.blocksize % 2 == 0) request.blocksize++;
            Imgproc.adaptiveThreshold(edges, edges, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, (int)request.blocksize, request.c);
            Imgproc.Canny(edges, edges, request.cannyThreshold, request.cannyThreshold * 3, 3, false);
            //Imgproc.cvtColor(edges, request.frame, Imgproc.COLOR_GRAY2BGR);

            // Get the lines from the edges.
            Imgproc.HoughLinesP(edges, lines, 1, Math.PI / 180, (int) request.linesThreshold, (int) request.minLineLength, (int) request.maxLineGap);
            for (int i = 0; i < lines.rows(); ++i) {
                lineList.add(new Line(lines.get(i, 0)));
            }

            // Get a list of corners at each line intersection.
            //ArrayList<Group> groups = new ArrayList<>();
            Point p;
            int groupNum = 0;

            for (int i = 0; i < lineList.size(); ++i) {
                for (int j = i + 1; j < lineList.size(); ++j) {
                    p = new Point();
                    Line a = lineList.get(i);
                    Line b = lineList.get(j);
                    getIntersection(a, b, p);
                    if (p.x >= 0 && p.y >= 0 && p.x < request.frame.width() && p.y < request.frame.width()) {
                        //Log.i(TAG, "intersection detected");
                        if (a.gid == -1 && b.gid == -1) {
                            ArrayList<Point> c = new ArrayList<>();
                            c.add(p);
                            corners.add(c);
                            a.gid = groupNum;
                            b.gid = groupNum;
                            groupNum++;
                        } else if (a.gid == -1 && b.gid != -1) {
                            corners.get(b.gid).add(p);
                            a.gid = b.gid;
                        } else if (a.gid != -1 && b.gid == -1) {
                            corners.get(a.gid).add(p);
                            b.gid = a.gid;
                        } else if (a.gid == b.gid) {
                            corners.get(a.gid).add(p);
                        } else {
                            for (int k = 0; k < corners.get(b.gid).size(); ++k) {
                                corners.get(a.gid).add(corners.get(b.gid).get(k));
                            }
                            corners.get(b.gid).clear();
                            b.gid = a.gid;
                        }
                    }
                }
            }

            // Group the intersecting lines together
            for (int i = 0; i < lineList.size(); ++i) {
                for (int j = i + 1; j < lineList.size(); ++j) {
                    p = new Point();
                    Line a = lineList.get(i);
                    Line b = lineList.get(j);
                    getIntersection(a, b, p);
                    if (p.x >= 0 && p.y >= 0 && p.x < request.frame.width() && p.y < request.frame.width()) {
                        //Log.i(TAG, "intersection detected");
                        if (a.group == null && b.group == null) {
                            Group g = new Group();
                            a.group = g;
                            b.group = g;
                            g.lines.add(a);
                            g.lines.add(b);
                            g.corners.add(p);
                            groups.add(g);
                        } else if (a.group == null && b.group != null) {
                            Group g = b.group;
                            a.group = g;
                            g.lines.add(a);
                            g.corners.add(p);
                        } else if (a.group != null && b.group == null) {
                            Group g = a.group;
                            b.group = g;
                            g.lines.add(b);
                            g.corners.add(p);
                        } else if (a.group != b.group){
                            for (int k = 0; k < a.group.lines.size(); ++k) {
                                b.group.lines.add(a.group.lines.get(k));
                                a.group.lines.get(k).group = b.group;
                            }
                            groups.remove(a.group);
                            a.group.destroy();
                            a.group = null;
                        }
                    }
                }
            }

            // Find the corners of each rectangle.
            Point center = new Point(0, 0);
            for (int i = 0; i < corners.size(); ++i) {
                if (corners.get(i).size() < 4) continue;
                center.x = 0;
                center.y = 0;
                ArrayList<Point> cList = corners.get(i);
                for (int j = 0; j < cList.size(); ++j) {
                    if (cList.get(j).x < 0 || cList.get(j).y < 0)
                        Log.i(TAG, "x: " + cList.get(j).x + " y: " + cList.get(j).y);
                    center.x += cList.get(j).x;
                    center.y += cList.get(j).y;
                }
                if (center.x < 0 || center.y < 0) {
                    Log.i(TAG, "LOL");
                }
                center.x /= (double) cList.size();
                center.y /= (double) cList.size();
                if (center.x == -1 || center.y == -1) Log.i(TAG, "We fucked up boys");
                sortCorners(cList, center);

                //Rect r = Imgproc.boundingRect(new MatOfPoint(cList.get(0), cList.get(1), cList.get(2), cList.get(3)));
                request.rects.add(new ArrayList<>(cList));
                //Imgproc.rectangle(request.frame, cList.get(0), cList.get(3), new Scalar(0, 0, 255));
                cList.clear();
            }

            request.lines = new ArrayList<>(lineList);
            lineList.clear();
            for (int i = 0; i < corners.size(); ++i) {
                corners.get(i).clear();
            }
            corners.clear();

            gray.release();
            edges.release();
            lines.release();

            //Core.add(output, Scalar.all(0), output);
            //frame.copyTo(output, edges);
            request.completed = true;
        }
    }*/
}
