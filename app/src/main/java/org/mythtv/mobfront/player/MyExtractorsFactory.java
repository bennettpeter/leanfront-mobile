package org.mythtv.mobfront.player;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.extractor.ts.TsPayloadReader;

import org.mythtv.mobfront.data.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UnstableApi
public class MyExtractorsFactory implements ExtractorsFactory {

    private DefaultExtractorsFactory defaultFactory;

    public MyExtractorsFactory() {
        this.defaultFactory = new DefaultExtractorsFactory();
    }

    @NonNull
    @Override
    public Extractor[] createExtractors() {
        Extractor[] exts = defaultFactory.createExtractors();
        updateExtractors(exts);
        return exts;
    }

    @OptIn(markerClass = UnstableApi.class) private void updateExtractors(Extractor[] exts) {
        for (int ix = 0; ix < exts.length; ix++) {
            if (exts[ix] instanceof TsExtractor) {
                List<Format> closedCaptionFormats = new ArrayList<>();
                closedCaptionFormats.add(
                        new Format.Builder()
                                .setAccessibilityChannel(1)
                                .setSampleMimeType(MimeTypes.APPLICATION_CEA608)
                                .build());
                closedCaptionFormats.add(
                        new Format.Builder()
                                .setAccessibilityChannel(2)
                                .setSampleMimeType(MimeTypes.APPLICATION_CEA608)
                                .build());
                TsPayloadReader.Factory payloadReaderFactory
                        =  new DefaultTsPayloadReaderFactory(
                        0,
                        closedCaptionFormats);
                TsExtractor ts = new TsExtractor(
                        TsExtractor.MODE_SINGLE_PMT,
                        0,
                        new DefaultSubtitleParserFactory(),
                        new TimestampAdjuster(0),
                        payloadReaderFactory,
                        Settings.getInt("pref_tweak_ts_search_pkts") * TsExtractor.TS_PACKET_SIZE);
                exts[ix] = ts;
            }
        }
    }
    @NonNull
    @Override
    public Extractor[] createExtractors(@NonNull Uri uri, @NonNull Map<String, List<String>> responseHeaders) {
        Extractor[] exts = defaultFactory.createExtractors(uri, responseHeaders);
        updateExtractors(exts);
        return exts;
    }
}
