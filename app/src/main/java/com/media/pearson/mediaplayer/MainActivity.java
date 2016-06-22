package com.media.pearson.mediaplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity {
    private TextView mUrlTextView;
    private String mURL;
    public static final String URL = "url";
    private String url = "http://download.wavetlan.com/SVV/Media/HTTP/MP4/ConvertedFiles/Media-Convert/Unsupported/test7.mp4";
//    private String url = "https://ia800201.us.archive.org/22/items/ksnn_compilation_master_the_internet/ksnn_compilation_master_the_internet_512kb.mp4";
//    private String url = "http://www.html5videoplayer.net/videos/toystory.mp4";
//    private String url = "https://eps.openclass.com/pulse-sa/api/item/0677821c-e903-41a4-9bb0-d784aafd535c/1/file/Additive_and_multiplicative_inverses_for_integers.mp4";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUrlTextView = (TextView) findViewById(R.id.url_container);
        mUrlTextView.setText(url);
    }

    public void submit(View v){
        Intent intent = new Intent(this, PlayerActivity.class);
        if(!TextUtils.isEmpty(mUrlTextView.getText())){
            mURL =String.valueOf( mUrlTextView.getText());
            intent.putExtra(URL,mURL);
            startActivity(intent);
        }else
            new AlertDialog.Builder(this).setTitle("Enter URL").setMessage("Please enter any video/audio url.").create().show();
    }
}
