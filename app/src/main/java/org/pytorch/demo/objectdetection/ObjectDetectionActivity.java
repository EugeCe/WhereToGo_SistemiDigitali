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
import android.util.DisplayMetrics;
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class ObjectDetectionActivity extends AbstractCameraXActivity<ObjectDetectionActivity.AnalysisResult>
        implements GestureDetector.OnDoubleTapListener , GestureDetector.OnGestureListener{

    public static final double DISTANCE_THRESHOLD = 0.55;
    private Module mModule = null;
    private ResultView mResultView;

    private String model_file;
    private String class_file;


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
    private Boolean isVoiceOn = true;

    //offset
    private int offset = 0;

    //timer
    private Timer timer;
    public static final int VOICE_INTERVAL = 5000;
    //end

    //display rect
    private float displayArea = 0;
    private float displayHeigh = 0;
    private Rect displayRect = null;

    //gestures
    private GestureDetectorCompat mDetector;


    @Override
    protected void onCreate(Bundle savedInstance) {

        super.onCreate(savedInstance);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        displayArea = height * width;
        displayHeigh = height;
        displayRect = new Rect(0,0, width, height);

        //loading properties
        try {
            Properties properties = new Properties();
            properties.load(getResources().openRawResource(R.raw.config));
            model_file = properties.getProperty("model.model_file");
            class_file = properties.getProperty("model.class_file");

            //prepostprocessor
            PrePostProcessor.mInputWidth = Integer.parseInt(properties.getProperty("model.inputWidth"));
            PrePostProcessor.mInputHeight = Integer.parseInt(properties.getProperty("model.inputHeight"));

            PrePostProcessor.mNmsLimit = Integer.parseInt(properties.getProperty("model.nmsLimit"));

            PrePostProcessor.mOutputColumn = Integer.parseInt(properties.getProperty("model.outputColumn"));

            PrePostProcessor.mOutputRow = Integer.parseInt(properties.getProperty("model.outputRow"));

            PrePostProcessor.mThreshold = Float.parseFloat(properties.getProperty("model.threshold"));

        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Object Detection", "Error reading config file", e);
            finish();
        }

        try {
            mModule = PyTorchAndroid.loadModuleFromAsset(getAssets(), model_file);
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open(class_file)));
            String line;
            List<String> classes = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                classes.add(line);
            }
            PrePostProcessor.mClasses = new String[classes.size()];
            classes.toArray(PrePostProcessor.mClasses);
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }

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

        if(isVoiceOn){
            speak("Voice off");
            isVoiceOn = false;
        } else {
            speak("Voice on");
            isVoiceOn = true;
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
        //mResultView.setResults(result.mResults, result.mTimes, average);
        Result bestDoor = new Result();
        //Result bestHandle = new Result();
        ArrayList<Result> results = new ArrayList<Result>();
        StringBuilder toSpeak = new StringBuilder();
        float percentage = 0;

        //recupero la miglior porta
        try {
            bestDoor = result.mResults.stream().filter(x -> x.classIndex == 0).max((x, y) -> (int) (x.score * 100 - y.score * 100)).get();
            results.add(bestDoor);

            float doorArea = bestDoor.rect.width() * bestDoor.rect.height();

            percentage = doorArea / displayArea;
            //percentage =  bestDoor.rect.height() / displayHeigh;

            //if(percentage < 50)
            //Log.d("percentage: " , percentage + "");
            //Log.d("display area: " , displayArea + "");
            //Log.d("door area: " , doorArea + "");


            int doorPosition = bestDoor.rect.centerX() - displayRect.centerX();
            boolean isNotOnTheCenter = Math.abs(doorPosition) > (displayRect.width()/3.5);

            Log.d("door centro: " , bestDoor.rect.centerX() + "");
            Log.d("door Larghez: " , bestDoor.rect.width() + "");

            toSpeak.append("Door detected ");
            if (percentage > DISTANCE_THRESHOLD)
                toSpeak.append("in front of you ");
            else {//here the voice about the handle is off
                if(isNotOnTheCenter)
                    toSpeak.append(doorPosition < 0 ? " on the left " : " on the right ");
                toSpeak.append(" far from you, get closer");
            }

        } catch(Exception e) {
            System.out.print("Porta non trovata");
        }


        //recupero la maniglia relativa alla porta trovata prima
        try {
            Result finalBestDoor = bestDoor;
            Result bestHandle = result.mResults.stream().filter(x -> x.classIndex == 1).filter(
                    x -> {
                        if (x.rect.intersect(finalBestDoor.rect))
                            return true;
                        return false;
                    }).findFirst().get();

            offset = bestDoor.rect.centerX() - bestHandle.rect.centerX();
            results.add(bestHandle);
            if (percentage > DISTANCE_THRESHOLD) {
                toSpeak.append("with an handle on the ");
                toSpeak.append(offset > 0 ? "left" : "right");
            }

        } catch (Exception e) {
            System.out.print("Non ho trovato maniglie");
        }


        mResultView.setResults(results, result.mTimes, average);

        //Todo Voice
        if ( isVoiceOn && canISpeak /*Useless computation if i cannot speak*/ && !result.mResults.isEmpty()) {
            canISpeak = false;
            //The computation cannot give many results
            /* int nDoors = (int) result.mResults.stream().
                        map(x -> x.classIndex).
                        filter(x -> x == 0).count();

                //The computation cannot give many results
                int nHandles = (int) result.mResults.stream().
                        map(x -> x.classIndex).
                        filter(x -> x == 1).count();

               if (nDoors != 0 && nHandles != 0) {
                toSpeak = nDoors + (nDoors == 1 ? " door and " : " doors and " ) + nHandles + (nHandles == 1 ? " handle" : " handles");
            }
            if (nDoors == 0 && nHandles != 0) {
                toSpeak = nHandles + (nHandles == 1 ? " handle" : " handles");
            }

            if (nDoors != 0 && nHandles == 0) {
                toSpeak = nDoors + (nDoors == 1 ? " door" : " doors" );
            }*/
            speak(toSpeak.toString());


            //String mClass = PrePostProcessor.mClasses[result.classIndex];

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
        if (isVoiceOn)
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
            mModule = PyTorchAndroid.loadModuleFromAsset(getAssets(), model_file);
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
