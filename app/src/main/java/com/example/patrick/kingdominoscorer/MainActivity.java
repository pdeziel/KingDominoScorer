package com.example.patrick.kingdominoscorer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteBindOrColumnIndexOutOfRangeException;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.opencv.android.JavaCamera2View;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.android.CameraBridgeViewBase;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.Mat;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import org.opencv.core.Size;

import java.lang.reflect.Array;
import java.util.Arrays;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Core;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.opencv.core.Rect;
import org.opencv.core.MatOfPoint;
import android.widget.SeekBar;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {
    private static final String TAG = "MainActivity";
    private static volatile FrameRequest request = null;
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSession;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private ImageReader imageReader;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private TextureView textureView;
    private CameraBridgeViewBase cameraPreview;
    private boolean mIsJavaCamera = true;
    private MenuItem mItemSwitchCamera = null;
    Mat mRgba;
    Mat output;
    ArrayList<ArrayList<Point>> rects;
    ArrayList<Line> lines;
    private TextView cannyThresholdText;
    private TextView linesThresholdText;
    private TextView minLineLengthText;
    private TextView maxLineGapText;
    private TextView blockSizeText;
    private TextView cText;
    Runnable r;
    Point matchLoc = null;

    final double SRC_H_MIN = 1;
    final double SRC_H_MAX = 360;
    final double SRC_S_MIN = 0;
    final double SRC_S_MAX = 100;
    final double SRC_V_MIN = 0;
    final double SRC_V_MAX = 100;
    final double CV_H_MIN = 0;
    final double CV_H_MAX = 179;
    final double CV_S_MIN = 0;
    final double CV_S_MAX = 255;
    final double CV_V_MIN = 0;
    final double CV_V_MAX = 255;

    SeekBar cannyThresholdSeek;
    SeekBar linesThresholdSeek;
    SeekBar minLineLengthSeek;
    SeekBar maxLineGapSeek;
    SeekBar blockSizeSeek;
    SeekBar cSeek;

    private Scalar red;
    private Scalar green;
    private Scalar blue;
    private Scalar orange;

    private Point boundsTL;
    private Point boundsTR;
    private Point boundsBL;
    private Point boundsBR;

    protected BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    cameraPreview.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        red = new Scalar(255, 0, 0);
        green = new Scalar(0, 255, 0);
        blue = new Scalar(0, 0, 255);
        orange = new Scalar(244, 149, 66);
        rects = new ArrayList<>();
        lines = new ArrayList<>();
        boundsTL = new Point(50, 50);
        boundsTR = new Point(500, 50);
        boundsBL = new Point(25, 200);
        boundsBR = new Point(525, 200);

        request = new FrameRequest();
        super.onCreate(savedInstanceState);
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        setContentView(R.layout.show_camera);
        cameraPreview = findViewById(R.id.show_camera_activity);
        //cameraPreview.setRotation(270);
        //Settings.System.putInt(this, Settings.System.SCREEN_BRIGHTNESS)
        cameraPreview.setCvCameraViewListener(this);
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        cannyThresholdSeek = findViewById(R.id.canny_threshold);
        cannyThresholdSeek.setOnSeekBarChangeListener(cannyThresholdListener);
        cannyThresholdText = findViewById(R.id.canny_threshold_text);
        cannyThresholdText.setText("Canny: " + cannyThresholdSeek.getProgress());

        linesThresholdSeek = findViewById(R.id.lines_threshold);
        linesThresholdSeek.setOnSeekBarChangeListener(linesThresholdListener);
        linesThresholdText = findViewById(R.id.lines_threshold_text);
        linesThresholdText.setText("Line Thresh: " + linesThresholdSeek.getProgress());

        minLineLengthSeek = findViewById(R.id.min_line_length);
        minLineLengthSeek.setOnSeekBarChangeListener(minLineLengthListener);
        minLineLengthText = findViewById(R.id.min_line_length_text);
        minLineLengthText.setText("Min Length: " + minLineLengthSeek.getProgress());

        maxLineGapSeek = findViewById(R.id.max_line_gap);
        maxLineGapSeek.setOnSeekBarChangeListener(maxLineGapListener);
        maxLineGapText = findViewById(R.id.max_line_gap_text);
        maxLineGapText.setText("Max Gap: " + maxLineGapSeek.getProgress());

        blockSizeSeek = findViewById(R.id.block_size);
        blockSizeSeek.setOnSeekBarChangeListener(blockSizeListener);
        blockSizeText = findViewById(R.id.block_size_text);
        blockSizeText.setText("Block Size: " + blockSizeSeek.getProgress());

        cSeek = findViewById(R.id.c);
        cSeek.setOnSeekBarChangeListener(cListener);
        cText = findViewById(R.id.c_text);
        cText.setText("C: " + cSeek.getProgress());
    }

    SeekBar.OnSeekBarChangeListener cListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            cText.setText("C: " + progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    SeekBar.OnSeekBarChangeListener blockSizeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            blockSizeText.setText("Blocksize: " + progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    SeekBar.OnSeekBarChangeListener cannyThresholdListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // updated continuously as the user slides the thumb
            cannyThresholdText.setText("Canny " + progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // called when the user first touches the SeekBar
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // called after the user finishes moving the SeekBar
        }
    };

    SeekBar.OnSeekBarChangeListener linesThresholdListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // updated continuously as the user slides the thumb
            linesThresholdText.setText("Line Thresh: " + progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // called when the user first touches the SeekBar
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // called after the user finishes moving the SeekBar
        }
    };

    SeekBar.OnSeekBarChangeListener minLineLengthListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // updated continuously as the user slides the thumb
            minLineLengthText.setText("Min Length: " + progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // called when the user first touches the SeekBar
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // called after the user finishes moving the SeekBar
        }
    };

    SeekBar.OnSeekBarChangeListener maxLineGapListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // updated continuously as the user slides the thumb
            maxLineGapText.setText("Max Gap: " + progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // called when the user first touches the SeekBar
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // called after the user finishes moving the SeekBar
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(cameraPreview != null){
            cameraPreview.disableView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, this, mLoaderCallback);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba =  new Mat(height, width, CvType.CV_8UC4);
        output = new Mat();
        r = new FrameAnalyzer(this, request);
        new Thread(r).start();
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    /* Normalize a number from one range to another. */
    private double normalize(double value, double src_min, double src_max, double dest_min, double dest_max) {
        return (((value - src_min) * (dest_max - dest_min)) / (src_max - src_min)) + dest_min;
    }

    private double seek_normalize(double value, double dest_min, double dest_max) {
        return normalize(value, 0, 100, dest_min, dest_max);
    }

    private double h_norm(double value) {
        return normalize(value, SRC_H_MIN, SRC_H_MAX, CV_H_MIN, CV_H_MAX);
    }

    private double s_norm(double value) {
        return normalize(value, SRC_S_MIN, SRC_S_MAX, CV_S_MIN, CV_S_MAX);
    }

    private double v_norm(double value) {
        return normalize(value, SRC_V_MIN, SRC_V_MAX, CV_V_MIN, CV_V_MAX);
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        output.release();
        output = inputFrame.rgba();
        if (request.state == FrameRequest.State.INVALID) {
            request.frame = inputFrame.rgba().clone();
            request.cannyThreshold = seek_normalize(cannyThresholdSeek.getProgress(), 1, 20);
            request.linesThreshold = seek_normalize(linesThresholdSeek.getProgress(), 1, 200);
            request.minLineLength = seek_normalize(minLineLengthSeek.getProgress(), 0, 200);
            request.maxLineGap = seek_normalize(maxLineGapSeek.getProgress(), 0, 50);
            request.blocksize = seek_normalize(blockSizeSeek.getProgress(), 3, 100);
            request.c = seek_normalize(cSeek.getProgress(), 0, 100);
            request.state = FrameRequest.State.PENDING;
        } else if (request.state == FrameRequest.State.COMPLETED) {
            request.state = FrameRequest.State.INVALID;
            for (int i = 0; i < rects.size(); ++i) {
                rects.get(i).clear();
            }
            rects.clear();
            for (int i = 0; i < request.rects.size(); ++i) {
                rects.add(new ArrayList<>(request.rects.get(i)));
                request.rects.get(i).clear();
            }
            lines.clear();
            lines = new ArrayList<>(request.lines);

            request.rects.clear();
            request.lines.clear();
        }

        //Imgproc.line(output, boundsTL, boundsTR, green);
        //Imgproc.line(output, boundsBL, boundsTL, green);
        //Imgproc.line(output, boundsBL, boundsBR, green);
        //Imgproc.line(output, boundsBR, boundsTR, green);

        for (int i = 0; i < lines.size(); ++i) {
            Imgproc.line(output, lines.get(i).p1, lines.get(i).p2, blue, 3, Imgproc.LINE_AA, 0);
            Imgproc.circle(output, lines.get(i).p1, 10, red);
        }

        for (int i = 0; i < rects.size(); ++i) {
            Imgproc.rectangle(output, rects.get(i).get(0), rects.get(i).get(1), i == 0 ? red : i == 1 ? green : i == 2 ? blue : orange, 3);
        }

        //Imgproc.putText(output, "lines: " + lines.size() + "rects: " + rects.size(), new Point(50, 50), Core.FONT_HERSHEY_PLAIN, 2, green);

        Mat outputT = new Mat(output.height(), output.width(), output.type());
        Mat outputF = new Mat(output.height(), output.width(), output.type());
        Core.transpose(output, outputT);
        Imgproc.resize(outputT, outputF, outputF.size());
        Core.flip(outputF, output, 1);
        outputT.release();
        outputF.release();
        return output;

        //Scalar min_green = new Scalar(h_norm(70), s_norm(50), v_norm(20));
        //Scalar max_green = new Scalar(h_norm(140), s_norm(100), v_norm(100));
        //Scalar min_blue = new Scalar(110, 50, 50);
        //Scalar max_blue = new Scalar(130, 255, 255);
    }
}


