package org.pytorch.demo.objectdetection;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.ViewStub;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.camera.core.ImageProxy;
import androidx.core.view.GestureDetectorCompat;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.PyTorchAndroid;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ObjectDetectionActivity extends AbstractCameraXActivity<ObjectDetectionActivity.AnalysisResult>
        implements GestureDetector.OnDoubleTapListener , GestureDetector.OnGestureListener{
    private Module mModule = null;
    private ResultView mResultView;

    //debug
    private float averageBitmapTime = 0;
    private long nBitmapTime = 0;

    //average
    private float average = 0;
    private long nAverage = 0;

    //Todo Voice variables
    //EditText write;
    private TextToSpeech ttobj;
    private Boolean canISpeak = true;


    //timer
    private Timer timer;
    public static final int VOICE_INTERVAL = 1500;
    //end

    //gestures
    private GestureDetectorCompat mDetector;


    @Override
    protected void onCreate(Bundle savedInstance) {

        super.onCreate(savedInstance);

        //Todo Gesture
        mDetector = new GestureDetectorCompat(this, this);
        mDetector.setOnDoubleTapListener(this);

        //end

        //ToDo init Voice
        ttobj = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
            }
        });
        ttobj.setLanguage(Locale.UK);

        timer = new Timer();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (this.mDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }


    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {

        if(canISpeak){
            speak("Voice off");
            canISpeak = false;
        } else {
            speak("Voice on");
            canISpeak = true;
        }

        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }


    static class AnalysisResult {
        private final ArrayList<Result> mResults;
        private final long mTimes;
        private final float mAverage;


        public AnalysisResult(ArrayList<Result> results, long times) {
            mResults = results;
            mTimes = times;
            mAverage = -1;
        }

        public AnalysisResult(ArrayList<Result> results, long times, float average) {
            mResults = results;
            mTimes = times;
            mAverage = average;
        }

        public AnalysisResult(ArrayList<Result> results) {
            mResults = results;
            mTimes = -1;
            mAverage = -1;

        }
    }

    @Override
    protected int getContentViewLayoutId() {
        return R.layout.activity_object_detection;
    }

    @Override
    protected TextureView getCameraPreviewTextureView() {
        mResultView = findViewById(R.id.resultView);
        return ((ViewStub) findViewById(R.id.object_detection_texture_view_stub))
                .inflate()
                .findViewById(R.id.object_detection_texture_view);
    }

    @Override
    protected void applyToUiAnalyzeImageResult(AnalysisResult result) {
        mResultView.setResults(result.mResults, result.mTimes, average);

        //Todo Voice
        if (!result.mResults.isEmpty() && canISpeak /*Useless computation if i cannot speak*/) {

            long nDoors = result.mResults.stream().map(x ->
                            x.classIndex
                    //PrePostProcessor.mClasses[]
            ).filter(
                    x -> x == 0).count();

            long nHandles = result.mResults.stream().map(x ->
                            x.classIndex
                    //PrePostProcessor.mClasses[]
            ).filter(
                    x -> x == 1).count();

            String toSpeak = "";
            if (nDoors != 0 && nHandles != 0) {
                toSpeak = nDoors + (nDoors == 1 ? " door and " : " doors and " ) + nHandles + (nHandles == 1 ? " handle" : " handles");
            }
            if (nDoors == 0 && nHandles != 0) {
                toSpeak = nHandles + (nHandles == 1 ? " handle" : " handles");
            }

            if (nDoors != 0 && nHandles == 0) {
                toSpeak = nDoors + (nDoors == 1 ? " door" : " doors" );
            }
            speak(toSpeak);

            //String mClass = PrePostProcessor.mClasses[result.classIndex];

            canISpeak = false;
            //If i double taped the screen here
            //there is inconsistence possibitity.
            //It can be fixed with another boolean "voiceOn"
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    canISpeak = true;
                }

            }, VOICE_INTERVAL);
        }

        mResultView.invalidate();


    }


    private void speak(String toSpeak) {
        if (canISpeak)
            ttobj.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, "");
    }

    /*
        @Override
        protected void applyToUiAnalyzeImageResult(AnalysisResult result, long timeElapsed) {
            mResultView.setResults(result.mResults, timeElapsed);
            mResultView.invalidate();
        }
    */
    private Bitmap imgToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    @Override
    @WorkerThread
    @Nullable
    protected AnalysisResult analyzeImage(ImageProxy image, int rotationDegrees) {

        //toDo added in order to get analysis time
        long mLastAnalysisStartTime = SystemClock.elapsedRealtime();

        if (mModule == null) {
            mModule = PyTorchAndroid.loadModuleFromAsset(getAssets(), "yolov5s.torchscript.pt");
        }

        //toDo added in order to get bitmap time
        long mBitmapStartTime = SystemClock.elapsedRealtime();

        Bitmap bitmap = imgToBitmap(image.getImage());
        Matrix matrix = new Matrix();
        matrix.postRotate(90.0f);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);

        //toDo added in order to get bitmap time
        long mBitmapEndTime = SystemClock.elapsedRealtime();
        long  mBitmapElapsed = mBitmapEndTime - mBitmapStartTime;
        averageBitmapTime = ( (averageBitmapTime*nBitmapTime) + mBitmapElapsed ) / (nBitmapTime+1);
        nBitmapTime = nBitmapTime + 1;
        Log.d("BitMap_time AVERAGE:",averageBitmapTime + " ms | n= " + nBitmapTime );


        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();

        float imgScaleX = (float) bitmap.getWidth() / PrePostProcessor.mInputWidth;
        float imgScaleY = (float) bitmap.getHeight() / PrePostProcessor.mInputHeight;
        float ivScaleX = (float) mResultView.getWidth() / bitmap.getWidth();
        float ivScaleY = (float) mResultView.getHeight() / bitmap.getHeight();

        final ArrayList<Result> results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0, 0);

        //toDo added in order to get analysis time
        long mLastAnalysisEndTime = SystemClock.elapsedRealtime();
        long timeElapsed = mLastAnalysisEndTime - mLastAnalysisStartTime;
        average = ( (average*nAverage) + timeElapsed ) / (nAverage+1);
        nAverage = nAverage + 1;
        Log.d("OverAllTime AVERAGE:",average + " ms | n= " + nAverage);

        return new AnalysisResult(results, timeElapsed, average);
    }
}
