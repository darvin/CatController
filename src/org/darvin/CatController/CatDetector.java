package org.darvin.CatController;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONException;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.video.Video;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by darvin on 4/5/16.
 */
public class CatDetector{
    private Mat mCurrentHistogram = null;

    public void setCurrentHistogramAsCatHistogram(int index) {

        cat1historam = mCurrentHistogram;
    }

    public interface OnCatDetectedListener{
        public void onCatDetected(int index);   //method, which can have parameters
    }

    private OnCatDetectedListener mListener; //listener field

    //setting the listener
    public void setCatDetectedListener(OnCatDetectedListener eventListener) {
        this.mListener=eventListener;
    }

    private static final String TAG = "CatDetector";
    BackgroundSubtractor mBackgroundSubstractor;

    public CatDetector(String cat1HistogramBase64) {
        mBackgroundSubstractor = Video.createBackgroundSubtractorKNN(100, 400, false);

        setCatHistogram(cat1HistogramBase64, 0);
    }

    public void setCatHistogram(String cat1HistogramBase64, int index) {
        if (cat1HistogramBase64!=null) {
            try {
                cat1historam = SerializationUtils.matFromJson(cat1HistogramBase64);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    public String getCatHistogram(int index) {
        try {
            return SerializationUtils.matToJson(cat1historam);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }



    Mat cat1historam = null;

    long lastFrameProcessed = System.currentTimeMillis();
    long PROCESS_EVERY = 500;
    Mat lastFrame = null;

    static Mat calculateHist(Mat image, Mat mask) {
        int channelsNumber = image.channels() - 1;
        List<Mat> images = new ArrayList<Mat>(channelsNumber);
        Core.split(image, images);


        Mat hist3D = new Mat();
        List<Mat> histList = Arrays.asList( new Mat[] {new Mat(), new Mat(), new Mat()} );

        MatOfInt histSize = new MatOfInt(16);
        MatOfFloat ranges = new MatOfFloat(0f, 256f);

        for(int i=0; i<channelsNumber; i++)
        {
            Imgproc.calcHist(images, new MatOfInt(i), mask, histList.get(i), histSize, ranges);
        }

        Core.merge(histList, hist3D);
//        Mat normalizedHist = new Mat();
//        Core.normalize(hist3D, normalizedHist);

        return hist3D;
    }

    boolean matchHist(Mat hist) {
//        int channelsNumber = image.channels() - 1;
//        List<Mat> hists = new ArrayList<Mat>(channelsNumber);
//        Core.split(image, images);

        double compResult = Imgproc.compareHist(cat1historam, hist, Imgproc.CV_COMP_CORREL);
        Log.d(TAG, "CompResult: "+compResult);
        return compResult>0.8;
    }



    public Mat processFrame(Mat orig) {

        if (!orig.isContinuous() || orig.empty() || orig.type()==-1) {
            return new Mat();
        }



        Mat grey = new Mat();

        Imgproc.cvtColor(orig, grey, Imgproc.COLOR_BGR2GRAY);
//        Mat blurred = new Mat(orig.size(), orig.type());
//        Imgproc.GaussianBlur(orig, blurred, new Size(21, 21), 0);
//        Mat equalized = new Mat(orig.size(), orig.type());

//        Imgproc.equalizeHist(orig, equalized);

        Mat mask = new Mat(grey.size(), grey.type());
        mBackgroundSubstractor.apply(grey, mask);

        mCurrentHistogram = calculateHist(orig, mask);


        if (cat1historam != null) {
            boolean catDetected = matchHist(mCurrentHistogram);
            Log.d(TAG, "CAT DETECTED: "+catDetected);

            if (catDetected) {
                mListener.onCatDetected(0);
            }
        } else {
            Log.d(TAG, "CAT CANNOT BE DETECTED, TRAIN FIRST");

        }




        Mat masked = new Mat(orig.size(), orig.type());
        orig.copyTo(masked, mask);
        lastFrame = masked;
        return masked;
    }

}
