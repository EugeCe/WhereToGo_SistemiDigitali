// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

package org.pytorch.demo.objectdetection;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;


public class ResultView extends View {

    private final static int TEXT_X = 40;
    private final static int TEXT_Y = 35;
    private final static int TEXT_WIDTH = 260;
    private final static int TEXT_HEIGHT = 50;

    private Paint mPaintRectangle;
    private Paint mPaintText;

    private Paint mPaintText_time;
    private Paint mPaintText_average;


    private ArrayList<Result> mResults;
    private long timeElapsed;
    private float timeAverage = 0;

    public ResultView(Context context) {
        super(context);
    }

    public ResultView(Context context, AttributeSet attrs){
        super(context, attrs);
        mPaintRectangle = new Paint();
        mPaintRectangle.setColor(Color.YELLOW);
        mPaintText = new Paint();

        mPaintText_time = new Paint();
        mPaintText_average = new Paint();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mResults == null) return;

        int xPos = (canvas.getWidth() / 2);
        int yPos = (int) canvas.getHeight() - TEXT_Y;

        for (Result result : mResults) {

            mPaintRectangle.setStrokeWidth(5);
            mPaintRectangle.setStyle(Paint.Style.STROKE);
            canvas.drawRect(result.rect, mPaintRectangle);

            Path mPath = new Path();
            RectF mRectF = new RectF(result.rect.left, result.rect.top, result.rect.left + TEXT_WIDTH,  result.rect.top + TEXT_HEIGHT);
            mPath.addRect(mRectF, Path.Direction.CW);
            mPaintText.setColor(Color.MAGENTA);
            canvas.drawPath(mPath, mPaintText);

            mPaintText.setColor(Color.WHITE);
            mPaintText.setStrokeWidth(0);
            mPaintText.setStyle(Paint.Style.FILL);
            mPaintText.setTextSize(32);
            String mClass = PrePostProcessor.mClasses[result.classIndex];
            //canvas.drawText(String.format("%s %.2f time: %d ms", mClass, result.score, timeElapsed), result.rect.left + TEXT_X, result.rect.top + TEXT_Y, mPaintText);
            canvas.drawText(String.format("%s %.2f", mClass, result.score), result.rect.left + TEXT_X, result.rect.top + TEXT_Y, mPaintText);

            //end_result
            //start_time

            /*
            Path mPath_time = new Path();
            @SuppressLint("DrawAllocation") RectF mRectF_time = new RectF(xPos,yPos, xPos + TEXT_WIDTH,  yPos + TEXT_HEIGHT);
            mPath_time.addRect(mRectF_time, Path.Direction.CW);
            mPaintText_time.setColor(Color.GREEN);
            canvas.drawPath(mPath_time, mPaintText_time);
             */


            mPaintText_time.setColor(Color.WHITE);
            mPaintText_time.setStrokeWidth(0);
            mPaintText_time.setStyle(Paint.Style.FILL_AND_STROKE);
            mPaintText_time.setTextSize(56);
            mPaintText_time.setTextAlign(Paint.Align.CENTER);

            canvas.drawText(String.format("time: %d ms", timeElapsed), xPos, yPos - 2*TEXT_Y, mPaintText_time );

        }


        //average
        mPaintText_average.setColor(Color.WHITE);
        mPaintText_average.setStrokeWidth(0);
        mPaintText_average.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaintText_average.setTextSize(56);
        mPaintText_average.setTextAlign(Paint.Align.CENTER);


        //((textPaint.descent() + textPaint.ascent()) / 2) is the distance from the baseline to the center.

        canvas.drawText(String.format("average: %.2f ms", timeAverage), xPos, yPos, mPaintText_average);

    }

    public void setResults(ArrayList<Result> results) {
        mResults = results;
    }


    public void setResults(ArrayList<Result> results, long timeElapsed) {
        mResults = results;
        this.timeElapsed = timeElapsed;
    }

    public void setResults(ArrayList<Result> results, long timeElapsed, float timeAverage) {
        mResults = results;
        this.timeElapsed = timeElapsed;
        this.timeAverage = timeAverage;
    }
}
