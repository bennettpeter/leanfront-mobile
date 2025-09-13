package org.mythtv.lfmobile.data;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class MythHttpDataSource extends BaseDataSource implements DataSource {

    private DataSpec mDataSpec;
    //    private PlaybackFragment mPlaybackFragment;
    private HttpDataSource mHttpDataSource;
    private long mTotalLength;
    private long mCurrentPos;
    private static final String TAG = "lfe";
    private static final String CLASS = "MythHttpDataSource";


    public MythHttpDataSource(String userAgent) {
        super(true);
        Map<String, String> defaultRequestProperties = new HashMap<>();
        defaultRequestProperties.put("accept-encoding","identity");
        String auth = BackendCache.getInstance().authorization;
        if (auth != null && auth.length() > 0)
            defaultRequestProperties.put("Authorization",auth);
        mHttpDataSource = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(defaultRequestProperties)
                .createDataSource();
    }

    @Override
    public long open(DataSpec dataSpec)
            throws IOException {
        this.mDataSpec = new DataSpec.Builder()
                .setUri(dataSpec.uri)
                .setHttpMethod(dataSpec.httpMethod)
                .setHttpBody(dataSpec.httpBody)
                .setPosition(dataSpec.position)
                .setLength(dataSpec.length)
                .setKey(dataSpec.key)
                .setFlags(dataSpec.flags)
                .build();
        long leng = 0;
        try {
            leng = mHttpDataSource.open(mDataSpec);
        } catch (HttpDataSource.InvalidResponseCodeException e) {
            // Response code 416 = read past eof
            if (e.responseCode == 416) {
                leng = 0;
                Log.i(TAG, CLASS + " End of file.");
            }
            else {
                Log.e(TAG, CLASS + " Bad Http Response Code:" +e.responseCode
                        + " " + e.responseMessage);
                throw e;
            }
        }
        mTotalLength = mDataSpec.position + leng;
        mCurrentPos = mDataSpec.position;
        return leng;
    }

    public int read(@NonNull byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength <= 0)
            return 0;
        int leng = mHttpDataSource.read(buffer,offset,readLength);
        if (leng == -1) {
            leng = 0;
        }
        if (leng == 0) {
            DataSpec dataSpec2 = new DataSpec.Builder()
                    .setUri(mDataSpec.uri)
                    .setHttpMethod(mDataSpec.httpMethod)
                    .setHttpBody(mDataSpec.httpBody)
                    .setPosition(mCurrentPos + leng)
                    .setLength(mDataSpec.length)
                    .setKey(mDataSpec.key)
                    .setFlags(mDataSpec.flags)
                    .build();
            mHttpDataSource.close();

            long leng2 = 0;
            try {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // Ignore this exception.
                }
                leng2 = mHttpDataSource.open(dataSpec2);
            } catch (HttpDataSource.InvalidResponseCodeException e) {
                // Response code 416 = read past eof
                if (e.responseCode == 416) {
                    leng2 = 0;
                    Log.i(TAG, CLASS + " End of file. (http code 416)");
                }
                else {
                    Log.e(TAG, CLASS + " Bad Http Response Code:" +e.responseCode
                            + " " + e.responseMessage);
                    throw e;
                }
            }
            long totalLength2 = dataSpec2.position + leng2;
            Log.d(TAG, CLASS + " Incremental data length:" + leng2);
            if (totalLength2 > mTotalLength) {
                mTotalLength = totalLength2;
                leng = mHttpDataSource.read(buffer, offset, readLength);
                mCurrentPos = dataSpec2.position;
                mDataSpec = dataSpec2;
            }
        }
        if (leng > 0)
            mCurrentPos += leng;
        else
            leng = C.RESULT_END_OF_INPUT;
        return leng;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return mDataSpec.uri;
    }

    @Override
    public void close() throws IOException {
        mHttpDataSource.close();
    }

    public long getCurrentPos() {
        return mCurrentPos;
    }

    public static class Factory implements DataSource.Factory {
        private String mUserAgent;

        public Factory(String userAgent) {
            mUserAgent = userAgent;
        }

        @NonNull
        @Override
        public DataSource createDataSource() {
            return new MythHttpDataSource(mUserAgent);
        }
    }

}
