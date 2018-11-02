package fill.storage.journey.fillstorage;

import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Paths;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "FillStorage";
    private static final long BYTE_IN_KB = 1024 ;
    private static final long BYTE_IN_MB = 1024 * BYTE_IN_KB;
    //private static final long BYTE_IN_GB = 1024 * BYTE_IN_MB;
    private TextView mStorageSize;
    private TextView mDataSize;
    private TextView mAllocatedSize;
    ProgressDialog mPd;
    private UiHandler mUiHandler;
    private BgHandler mBgHandler;

    private boolean mResumed = false;
    private static final String[] PERMISSIONS_STORAGE = {
            "android.permission.WRITE_EXTERNAL_STORAGE" };


    private void verifyStoragePermissions() {

        try {
            int permission = ActivityCompat.checkSelfPermission(this,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE,0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStorageSize = findViewById(R.id.StorageSize);
        mDataSize = findViewById(R.id.DataSize);
        mAllocatedSize =  findViewById(R.id.AllocatedSize);
        mPd = new ProgressDialog(this);
        mPd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mPd.setCancelable(false);
        HandlerThread mHandlerThread = new HandlerThread("bgHandlerThread");
        mHandlerThread.start();
        mBgHandler = new BgHandler(mHandlerThread.getLooper());
        mUiHandler = new UiHandler();
        updateFreeSize();
        verifyStoragePermissions();
    }

    private void updateUiRequest() {
        updateUiRequest(0);
    }
    private void updateUiRequest(long delay) {
        mUiHandler.sendEmptyMessageDelayed(UiHandler.MSG_UPDATE,delay);
    }


    private String makeDummyFileName(File path) {
        return  Paths.get(path.getPath(),"bigfile").toString();
    }
    private void cleanSpace(File path) {
        String fillSpaceFilePath = makeDummyFileName(path);
        File fillSpaceFile = new File(fillSpaceFilePath);
        Log.d(TAG,"try to delete a fillSpace file " + fillSpaceFile);
        if(fillSpaceFile.exists()) {
            Log.i(TAG," file " + fillSpaceFile + " delete , and release " + Formatter.formatFileSize(this, fillSpaceFile.length()) + "");
            fillSpaceFile.delete();
        }
        updateFreeSize();
    }
    private void fillSpace(File path) {
        long usableSpace = path.getUsableSpace();
        String fillSpaceFilePath = makeDummyFileName(path);
        Log.d(TAG,"try to create a fillSpace file " + fillSpaceFilePath);
        try {
            byte[] dummyData = new byte[(int) (100 * BYTE_IN_MB)];
            RandomAccessFile randomFile = new RandomAccessFile(fillSpaceFilePath, "rws");
            randomFile.seek(randomFile.length());
            mPd.setMax((int) (usableSpace / dummyData.length));
            //randomFile.setLength(usableSpace);
            while(usableSpace > 0) {
                randomFile.write(dummyData);
                mPd.incrementProgressBy(1);
                usableSpace = path.getUsableSpace();
                Log.i(TAG," usableSpace change to " + usableSpace + " as " + Formatter.formatFileSize(this, usableSpace));
            }
            Log.i(TAG,fillSpaceFilePath + " length change to " + randomFile.length() + " as " + Formatter.formatFileSize(this, randomFile.length()));
            randomFile.close();
        } catch (Exception e) {
            Log.e(TAG,"RandomAccessFile create failed.",e);
        } finally {
            updateFreeSize();
        }
    }

    private void updateAllocatedSize() {
        long allocated = 0;

        allocated += updateAllocatedSize(new File(makeDummyFileName(Environment.getExternalStorageDirectory())));
        allocated += updateAllocatedSize(new File(makeDummyFileName(getFilesDir())));

        String msg = Formatter.formatFileSize(this,allocated);
        if(!msg.equals(mAllocatedSize.getText().toString())) {
            mAllocatedSize.setText(msg);
        }
    }
    private long updateAllocatedSize(File path) {
        if(path.exists()) {
            return path.length();
        } else {
            return 0;
        }
    }

    void updateFreeSize() {
        updateFreeSize(Environment.getExternalStorageDirectory(),mStorageSize);
        updateFreeSize(getFilesDir(),mDataSize);
    }

    void updateFreeSize(File path,TextView view) {
        long freeSpace = path.getFreeSpace();
        long usableSpace = path.getUsableSpace();
        long totalSpace = path.getTotalSpace();
        String msg = Formatter.formatFileSize(this,usableSpace) + " (" + Formatter.formatFileSize(this, freeSpace) + ") / " + Formatter.formatFileSize(this,totalSpace);
        if(!msg.equals(view.getText().toString())) {
            /*
            Log.i(TAG, path
                    + " freeSpace："  + Formatter.formatFileSize(this,freeSpace)
                    + " usableSpace:" + Formatter.formatFileSize(this,usableSpace)
                    + " totalSpace:" + Formatter.formatFileSize(this,totalSpace));
            */
            view.setText(msg);
        }
    }

    public void onClick(View view) {
        // show it quickly
        mPd.setProgress(0);
        mPd.show();

        switch (view.getId())
        {
            case R.id.buttonFillStorage:
                mBgHandler.sendEmptyMessage(BgHandler.MSG_FILL_STORAGE);
                break;
            case R.id.buttonFillData:
                mBgHandler.sendEmptyMessage(BgHandler.MSG_FILL_DATA);
                break;
            case R.id.buttonRestore:
                mBgHandler.sendEmptyMessage(BgHandler.MSG_RESTORE_SIZE);
                break;
            default:
                break;
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        updateUiRequest();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
    }
    class UiHandler extends Handler {
        static final int MSG_UPDATE = 100;
        static final int MSG_PROCESS_SHOW = 101;
        static final int MSG_PROCESS_HIDE = 102;

        @Override
        public void handleMessage(Message msg) {
            removeMessages(msg.what);
            switch(msg.what) {
                case MSG_UPDATE:
                    if(mResumed) {
                        updateFreeSize();
                        updateAllocatedSize();
                        updateUiRequest(10*1000);
                    }
                    break;
                case MSG_PROCESS_SHOW:
                    mPd.show();
                    break;
                case MSG_PROCESS_HIDE:
                    updateUiRequest();
                    mPd.hide();
                    break;
                default:
                    break;
            }
        }
    }

    class BgHandler extends Handler {
        static final int MSG_FILL_STORAGE = 100;
        static final int MSG_FILL_DATA = 101;
        static final int MSG_RESTORE_SIZE = 102;

        BgHandler(Looper looper) {
            super(looper);
        }
        void showProcess(boolean show) {
            mUiHandler.sendEmptyMessage(show?UiHandler.MSG_PROCESS_SHOW:UiHandler.MSG_PROCESS_HIDE);
        }
        @Override
        public void handleMessage(Message msg) {
            //Log.d(TAG ," msg " + msg);
            removeMessages(msg.what);
            switch(msg.what) {
                case MSG_FILL_STORAGE:
                    showProcess(true);
                    fillSpace(Environment.getExternalStorageDirectory());
                    showProcess(false);
                    break;
                case MSG_FILL_DATA:
                    showProcess(true);
                    fillSpace(getFilesDir());
                    showProcess(false);
                    break;
                case MSG_RESTORE_SIZE:
                    showProcess(true);
                    cleanSpace(Environment.getExternalStorageDirectory());
                    cleanSpace(getFilesDir());
                    showProcess(false);
                    break;
                default:
                    break;
            }
        }
    }
}
