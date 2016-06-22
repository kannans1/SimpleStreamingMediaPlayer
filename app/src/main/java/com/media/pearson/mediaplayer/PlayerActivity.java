package com.media.pearson.mediaplayer;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import static android.media.MediaPlayer.*;

public class PlayerActivity extends AppCompatActivity {
    private String mUrl;
    private String mfileName;
    private String mFileAbsolutePath;
    private String mMetaDataFileAbsolutePath;
    private String mLastModified;
    private final String ACTUAL_FILE_SIZE = "actualfilesize";
    private final String DOWNLOADED_FILE_SIZE = "downloadedfilesize";
    private final String LAST_MODIFIED = "lastmodified";
    private final String MIN_REQ_CONTENT_LENGTH = "minmumContentRequiredLength";
    private MediaPlayer mMediaPlayer;
    private ImageButton play;
    private ImageButton pause;
    private ImageButton rewind;
    private ImageButton forward;
    private VideoView mVideoView;
    private DownloadTask downloadTask;
    private int seekBarPosition = 0;
    private ProgressDialog mProgressBar;

    public Long miniContentTostartPlayer = new Long(1000);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        Intent intent = getIntent();
        mUrl = intent.getStringExtra(MainActivity.URL);
        mfileName = getUniqueNameWithURL();
        play = (ImageButton) findViewById(R.id.media_play_video);
        pause = (ImageButton) findViewById(R.id.media_pause);
        rewind = (ImageButton) findViewById(R.id.media_rew);
        forward = (ImageButton) findViewById(R.id.media_ff);
        mVideoView = (VideoView) findViewById(R.id.video_view);
        //Progress bar
        mProgressBar = new ProgressDialog(this);
        mProgressBar.setMessage("Please wait, Loading the video");
        mProgressBar.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressBar.setIndeterminate(true);

        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mProgressBar.show();    // Show progress bar until first valid chunk is rendered by the video player
                seekBarPosition = 0;
                if (isFilePresent(mfileName)) {
//                    Properties metaDataMap = new Properties();
                    Long mActualContentSize = null;
                    Long mDownloadedContentSize = null;
                    Long min_req_content = null;
                    try {
                        String metaDataFileName = getFilesDir().getAbsolutePath() + "/media/"+mfileName.substring(0, mfileName.lastIndexOf('.')) + ".properties";
                        BufferedReader in = new BufferedReader(new FileReader(metaDataFileName));
                        String str;
                        while ((str = in.readLine()) != null) {
                            String[] keyAndValue = str.split("=");
                            switch (keyAndValue[0]){
                                case MIN_REQ_CONTENT_LENGTH:
                                    min_req_content = Long.parseLong(keyAndValue[1]);
                                    break;
                                case ACTUAL_FILE_SIZE:
                                    mActualContentSize = Long.parseLong(keyAndValue[1]);
                                    break;
                                case DOWNLOADED_FILE_SIZE:
                                    mDownloadedContentSize = Long.parseLong(keyAndValue[1]);
                                    break;
                                case LAST_MODIFIED:
                                    setLastModified(keyAndValue[1]);
                            }
                        }
                        in.close();
                    } catch (FileNotFoundException e) {
                        Log.e("FileNotFound", "The file in path " + mFileAbsolutePath + " is not found");
                        e.printStackTrace();
                        if(mProgressBar.isShowing())
                            mProgressBar.dismiss();
                        new AlertDialog.Builder(PlayerActivity.this).setTitle("Unable to Render Video").setMessage("Sorry! Unable to process the video right now.").create().show();
                    } catch (IOException ie) {
                        Log.e("IOException", "The file in path " + mFileAbsolutePath + " is not readable");
                        ie.printStackTrace();
                        if(mProgressBar.isShowing())
                            mProgressBar.dismiss();
                        new AlertDialog.Builder(PlayerActivity.this).setTitle("Unable to Render Video").setMessage("Sorry! Unable to process the video right now.").create().show();
                    }catch (Exception e){
                        Log.e("Error","Cannot read metadata file");
                        e.printStackTrace();
                        if(mProgressBar.isShowing())
                            mProgressBar.dismiss();
                        new AlertDialog.Builder(PlayerActivity.this).setTitle("Unable to Render Video").setMessage("Sorry! Unable to process the video right now.").create().show();
                    }

                    if (mActualContentSize.equals(mDownloadedContentSize)) {
                        startPlayer();
                    } else {
                        Long[] params = {mDownloadedContentSize, mActualContentSize, min_req_content};
                        downloadTask = new DownloadTask();
                        downloadTask.execute(params);
                    }
                } else {
                    Long[] params = {null, null, null};
                    downloadTask = new DownloadTask();
                    downloadTask.execute(params);
                }
            }
        });
        //pause
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mVideoView != null && mVideoView.isPlaying()) {
                    mVideoView.pause();
                }
            }
        });

    }

    private String getUniqueNameWithURL() {
        if (!TextUtils.isEmpty(mUrl)) {
            try {
                URL url = new URL(mUrl);
                String[] urlSegments = (mUrl).split("/");
                return (mUrl).hashCode() + "_" + urlSegments[urlSegments.length - 1];
            } catch (MalformedURLException e) {
                Log.e("MalformedURL", "Url" + mUrl + "is not valid");
                e.printStackTrace();
            }
        }
        return null;
    }

    public boolean isFilePresent(String fileName) {
        mFileAbsolutePath = getFilesDir().getAbsolutePath() + "/media/" + fileName;
        File file = new File(mFileAbsolutePath);
        return file.exists();
    }

    public void setLastModified(String lastModified) {
        this.mLastModified = lastModified;
    }

    public String getLastModified() {
        return this.mLastModified;
    }

    private void startPlayer() {
        if (mVideoView != null) {
            Uri uri = Uri.parse(mFileAbsolutePath);
            mVideoView.setMediaController(new MediaController(this));
            mVideoView.setVideoURI(uri);
            mVideoView.requestFocus();
            mVideoView.setOnErrorListener(mErrorListener);
            mVideoView.setOnCompletionListener(mCompleteListener);
            mVideoView.setOnPreparedListener(mPreparedListener);
        }
    }

    private OnErrorListener mErrorListener = new OnErrorListener() {

        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            switch(extra){
                case MediaPlayer.MEDIA_ERROR_IO:
                    if (!mp.isPlaying()) {
                        mp.reset();
                        Uri uri = Uri.parse(mFileAbsolutePath);
                        mVideoView.setVideoURI(uri);
                        mVideoView.requestFocus();
                        mVideoView.setOnCompletionListener(mCompleteListener);
                        mVideoView.setOnPreparedListener(mPreparedListener);
                        mVideoView.setOnErrorListener(mErrorListener);
                    }
                    break;
                case MediaPlayer.MEDIA_ERROR_MALFORMED:
                    Log.v("error extra","media error malformed");
                    break;
                case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                    Log.v("error extra","media error unsupported");
                    break;
                case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                    Log.v("error extra","media error timed out");
                    break;
            }
            miniContentTostartPlayer = miniContentTostartPlayer + 1000;
            if (downloadTask != null) {
                downloadTask.isPlayerStarted = false;
            }
            return true;
        }
    };

    private OnCompletionListener mCompleteListener = new OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
//            seekBarPosition = mVideoView.getCurrentPosition();
//            mp.reset();
//            Uri uri = Uri.parse(mFileAbsolutePath);
//            mVideoView.setVideoURI(uri);
//            mVideoView.requestFocus();
//            mVideoView.setOnCompletionListener(mCompleteListener);
//            mVideoView.setOnPreparedListener(mPreparedListener);
//            mVideoView.setOnErrorListener(mErrorListener);
        }
    };

    private OnPreparedListener mPreparedListener = new OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            if(mProgressBar.isShowing())
                mProgressBar.dismiss();
            mVideoView.start();
            if (seekBarPosition != 0) {
                Log.i("SEEK BAR", "Seeking to position is "+ seekBarPosition);
                mVideoView.seekTo(seekBarPosition);
            }
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    seekBarPosition = mVideoView.getCurrentPosition();
                    Log.i("SEEK BAR", "Current position is "+ seekBarPosition);
                }
            },1000);
            mVideoView.setBackgroundResource(0);
        }
    };

    public void WriteMetaDataFile(String actualfileSize, String downloadedFileSize, String lastModified, String minimumContentRequiredToStartVideoView) {
        String fileName = getFilesDir().getAbsolutePath() + "/media/" + mfileName.substring(0, mfileName.lastIndexOf('.')) + ".properties";
        try {
            Properties properties = new Properties();
            properties.setProperty(ACTUAL_FILE_SIZE, actualfileSize);
            properties.setProperty(DOWNLOADED_FILE_SIZE, downloadedFileSize);
            properties.setProperty(LAST_MODIFIED, lastModified);
            properties.setProperty(MIN_REQ_CONTENT_LENGTH, minimumContentRequiredToStartVideoView);
            File f = new File(fileName);
            OutputStream out = new FileOutputStream(f);
            properties.store(out, "Metadata");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            return false;
        }
        NetworkInfo.State network = networkInfo.getState();
        return (network == NetworkInfo.State.CONNECTED || network == NetworkInfo.State.CONNECTING);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mVideoView.stopPlayback();
        if (downloadTask != null){
            downloadTask.cancel(true);//cancel download
            downloadTask.isDownloadCancelled = true;
        }
        //release all resources

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if(mProgressBar.isShowing())
            mProgressBar.dismiss();
        mVideoView.stopPlayback();
        if (downloadTask != null) {
            downloadTask.cancel(true);//cancel download
        }
        //release all resources
    }

    private class DownloadTask extends AsyncTask<Long, Void, Void> {
        private Long mDownloadedfileSize;
        private Long mActualFileSize;
        public boolean isPlayerStarted = false;
        public boolean isDownloadCancelled = false;
        //initialy setting minimum content as 1000 kb, will increase dynamically if the content cannot start the player

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (!isNetworkAvailable()) {
                Log.e("No INTERNET", "there is no internet connected");
                if(mProgressBar.isShowing())
                    mProgressBar.dismiss();
                new AlertDialog.Builder(PlayerActivity.this).setTitle("No Internet").setMessage("Sorry unable to download the video. Please connect to internet").create().show();
                return;
            }
        }

        @Override
        protected Void doInBackground(Long... params) {
            mDownloadedfileSize = params[0];
            mActualFileSize = params[1];
            if (params[2] != null) {
                miniContentTostartPlayer = params[2];
            }

            if (mDownloadedfileSize == null) {          // New Download
                File file = new File(getFilesDir().getAbsolutePath() + "/media");
                if (!file.exists()) {
                    file.mkdirs();
                }
                String path = file + "/" + mfileName;
                long fileSize = new File(path).length();
                try {
                    URL url = new URL(mUrl);
                    URLConnection connection = url.openConnection();
                    InputStream inputstream = connection.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(inputstream, 1024 * 5);
                    FileOutputStream outStream = fileSize == 0 ? new FileOutputStream(path) : new FileOutputStream(path, true);
                    byte[] buff = new byte[5 * 1024];

                    //Read bytes (and store them) until there is nothing more to read(-1)
                    int actualFileSize = connection.getContentLength();
                    String lastModified = connection.getHeaderField("Last-Modified");
                    WriteMetaDataFile(actualFileSize + "", 0 + "", lastModified, miniContentTostartPlayer + "");    // Create metadata file

                    int downloaded = 0;
                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        if (!isDownloadCancelled) {
                            outStream.write(buff, 0, len);
                            downloaded += len;
                            Log.v("DOWNLOADED Content", Integer.toString(downloaded));
                            WriteMetaDataFile(actualFileSize + "", downloaded + "", lastModified, miniContentTostartPlayer + "");    // update metadata file
                            if (miniContentTostartPlayer < downloaded) {
                                if (!isPlayerStarted) {
                                    publishProgress();
                                    isPlayerStarted = true;
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e("IOEXCEPTION", "Unable to read/write the file");
                    e.printStackTrace();
                    if(mProgressBar.isShowing())
                        mProgressBar.dismiss();
                    new AlertDialog.Builder(PlayerActivity.this).setTitle("Unable to Render Video").setMessage("Sorry! Unable to process the video right now.").create().show();
                }
            } else {
                if (mActualFileSize > mDownloadedfileSize) {        //Resume Download
                    try {
                        URL url = new URL(mUrl);
                        URLConnection connection = url.openConnection();
                        connection.setRequestProperty("Range", "bytes=" + mDownloadedfileSize + "-");
                        connection.setRequestProperty("If-Range", getLastModified());
                        connection.connect();
                        String lastModified = connection.getHeaderField("Last-Modified");
                        BufferedInputStream inStream = new BufferedInputStream(connection.getInputStream(), 1024);
                        RandomAccessFile file = new RandomAccessFile(mFileAbsolutePath, "rw");
//                        FileOutputStream outStream = new FileOutputStream(file);
                        file.seek(mDownloadedfileSize);
                        byte buffer[] = new byte[1024];
                        int count = 0;
                        while ((count = inStream.read(buffer)) != -1) {
//                            outStream.write(buffer, 0, count);
                            if(!isDownloadCancelled){
                                file.write(buffer);
                                mDownloadedfileSize += count;
                                Log.v("DOWNLOADED Content", mDownloadedfileSize+"");
                                if (miniContentTostartPlayer < mDownloadedfileSize) {
                                    WriteMetaDataFile(mActualFileSize+"", mDownloadedfileSize + "", lastModified, miniContentTostartPlayer + "");    // update metadata file
                                    if (mDownloadedfileSize >= mActualFileSize)
                                        break;
                                    if (!isPlayerStarted) {
                                        publishProgress();
                                        isPlayerStarted = true;
                                    }
                                }
                            }
                        }
//                        outStream.close();
                        inStream.close();
                    } catch (Exception e) {
                        Log.e("MALFORMED URL", "Url " + mUrl + " is invalid");
                        e.printStackTrace();
                        if(mProgressBar.isShowing())
                            mProgressBar.dismiss();
                        new AlertDialog.Builder(PlayerActivity.this).setTitle("Unable to Render Video").setMessage("Sorry! Unable to process the video right now.").create().show();
                    }


                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
            startPlayer();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if(mProgressBar.isShowing())
                mProgressBar.dismiss();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            Log.i("Cancel Download", "================Download cancelled===========================");
        }
    }
}
