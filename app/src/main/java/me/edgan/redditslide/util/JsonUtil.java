package me.edgan.redditslide.util;

import android.util.Log;
import com.fasterxml.jackson.databind.JsonNode;

import me.edgan.redditslide.Activities.GalleryImage;

import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;

/** Created by TacoTheDank on 04/04/2021. */
public class JsonUtil {
    private static final String TAG = "JsonUtil";

    public static void getGalleryData(final JsonNode data, final ArrayList<GalleryImage> urls) {
        for (JsonNode identifier : data.get("gallery_data").get("items")) {
            String mediaId = identifier.get("media_id").asText();
            if (data.has("media_metadata") && data.get("media_metadata").has(mediaId)) {
                JsonNode mediaNode = data.get("media_metadata").get(mediaId);

                // Create a base GalleryImage with the source data
                GalleryImage image = new GalleryImage(mediaNode.get("s"));

                // Set mediaId explicitly
                image.mediaId = mediaId;

                // Make sure metadata exists
                if (image.metadata == null) {
                    image.metadata = new GalleryImage.MediaMetadata();
                }

                // Set metadata fields that determine animation status
                if (mediaNode.has("e")) {
                    image.metadata.e = mediaNode.get("e").asText();

                    // Detect animated content based on the e field
                    if ("AnimatedImage".equals(image.metadata.e)) {
                        image.metadata.animated = true;
                    }
                }

                if (mediaNode.has("m")) {
                    image.metadata.m = mediaNode.get("m").asText();

                    // Also check MIME type for animation
                    if (image.metadata.m != null && image.metadata.m.contains("gif")) {
                        image.metadata.animated = true;
                    }
                }

                // Check URL for animation as additional fallback
                if (image.url != null && (image.url.endsWith(".gif") || image.url.endsWith(".gifv") || image.url.endsWith(".mp4"))) {
                    image.metadata.animated = true;
                }

                // Create the source properly if it doesn't exist
                if (image.metadata.source == null) {
                    image.metadata.source = new GalleryImage.MediaMetadata.Source();
                }

                // Ensure source URLs are correct for animated content
                JsonNode s = mediaNode.get("s");
                if (s.has("mp4")) {
                    image.metadata.source.mp4 = StringEscapeUtils.unescapeHtml4(s.get("mp4").asText());
                }
                if (s.has("gif")) {
                    image.metadata.source.gif = StringEscapeUtils.unescapeHtml4(s.get("gif").asText());
                }
                if (s.has("u")) {
                    image.metadata.source.u = StringEscapeUtils.unescapeHtml4(s.get("u").asText());
                }

                // Add preview images if available
                if (mediaNode.has("p") && mediaNode.get("p").isArray() && mediaNode.get("p").size() > 0) {
                    JsonNode previewArray = mediaNode.get("p");
                    image.metadata.p = new GalleryImage.MediaMetadata.Preview[previewArray.size()];

                    for (int i = 0; i < previewArray.size(); i++) {
                        JsonNode preview = previewArray.get(i);
                        GalleryImage.MediaMetadata.Preview p = new GalleryImage.MediaMetadata.Preview();

                        if (preview.has("u")) {
                            p.u = StringEscapeUtils.unescapeHtml4(preview.get("u").asText());
                        }
                        if (preview.has("x")) {
                            p.x = preview.get("x").asInt();
                        }
                        if (preview.has("y")) {
                            p.y = preview.get("y").asInt();
                        }

                        image.metadata.p[i] = p;
                    }
                } else {
                    Log.d(TAG, "No preview array found in mediaNode for mediaId: " + mediaId);
                }

                urls.add(image);
            }
        }
    }
}
