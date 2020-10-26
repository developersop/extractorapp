package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.search.InfoItemsSearchCollector;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getClientVersion;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getKey;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getValidJsonResponseBody;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

/*
 * Created by Christian Schabesberger on 22.07.2018
 *
 * Copyright (C) Christian Schabesberger 2018 <chris.schabesberger@mailbox.org>
 * YoutubeSearchExtractor.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class YoutubeSearchExtractor extends SearchExtractor {
    private JsonObject initialData;

    public YoutubeSearchExtractor(final StreamingService service, final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) throws IOException, ExtractionException {
        final String url = getUrl() + "&pbj=1";

        final JsonArray ajaxJson = getJsonResponse(url, getExtractorLocalization());

        initialData = ajaxJson.getObject(1).getObject("response");
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        return super.getUrl() + "&gl=" + getExtractorContentCountry().getCountryCode();
    }

    @Nonnull
    @Override
    public String getSearchSuggestion() throws ParsingException {
        final JsonObject itemSectionRenderer = initialData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents").getObject(0)
                .getObject("itemSectionRenderer");
        final JsonObject didYouMeanRenderer = itemSectionRenderer.getArray("contents").getObject(0)
                .getObject("didYouMeanRenderer");
        final JsonObject showingResultsForRenderer = itemSectionRenderer.getArray("contents").getObject(0)
                .getObject("showingResultsForRenderer");

        if (!didYouMeanRenderer.isEmpty()) {
            return JsonUtils.getString(didYouMeanRenderer, "correctedQueryEndpoint.searchEndpoint.query");
        } else if (showingResultsForRenderer != null) {
            return getTextFromObject(showingResultsForRenderer.getObject("correctedQuery"));
        } else {
            return "";
        }
    }

    @Override
    public boolean isCorrectedSearch() {
        final JsonObject showingResultsForRenderer = initialData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents").getObject(0)
                .getObject("itemSectionRenderer").getArray("contents").getObject(0)
                .getObject("showingResultsForRenderer");
        return !showingResultsForRenderer.isEmpty();
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        final InfoItemsSearchCollector collector = new InfoItemsSearchCollector(getServiceId());

        final JsonArray sections = initialData.getObject("contents").getObject("twoColumnSearchResultsRenderer")
                .getObject("primaryContents").getObject("sectionListRenderer").getArray("contents");

        Page nextPage = null;

        for (final Object section : sections) {
            if (((JsonObject) section).has("itemSectionRenderer")) {
                final JsonObject itemSectionRenderer = ((JsonObject) section).getObject("itemSectionRenderer");

                collectStreamsFrom(collector, itemSectionRenderer.getArray("contents"));

                nextPage = getNextPageFrom(itemSectionRenderer.getArray("continuations"));
            } else if (((JsonObject) section).has("continuationItemRenderer")) {
                nextPage = getNewNextPageFrom(((JsonObject) section).getObject("continuationItemRenderer"));
            }
        }

        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(final Page page) throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final InfoItemsSearchCollector collector = new InfoItemsSearchCollector(getServiceId());

        if (page.getId() == null) {
            final JsonArray ajaxJson = getJsonResponse(page.getUrl(), getExtractorLocalization());

            final JsonObject itemSectionContinuation = ajaxJson.getObject(1).getObject("response")
                    .getObject("continuationContents").getObject("itemSectionContinuation");

            collectStreamsFrom(collector, itemSectionContinuation.getArray("contents"));
            final JsonArray continuations = itemSectionContinuation.getArray("continuations");

            return new InfoItemsPage<>(collector, getNextPageFrom(continuations));
        } else {
            // @formatter:off
            final byte[] json = JsonWriter.string()
                .object()
                    .object("context")
                        .object("client")
                            .value("hl", "en")
                            .value("gl", getExtractorContentCountry().getCountryCode())
                            .value("clientName", "WEB")
                            .value("clientVersion", getClientVersion())
                            .value("utcOffsetMinutes", 0)
                        .end()
                        .object("request").end()
                        .object("user").end()
                    .end()
                    .value("continuation", page.getId())
                .end().done().getBytes("UTF-8");
            // @formatter:on

            final Map<String, List<String>> headers = new HashMap<>();
            headers.put("Origin", Collections.singletonList("https://www.youtube.com"));
            headers.put("Referer", Collections.singletonList(this.getUrl()));
            headers.put("Content-Type", Collections.singletonList("application/json"));

            final String responseBody = getValidJsonResponseBody(getDownloader().post(page.getUrl(), headers, json));

            final JsonObject ajaxJson;
            try {
                ajaxJson = JsonParser.object().from(responseBody);
            } catch (JsonParserException e) {
                throw new ParsingException("Could not parse JSON", e);
            }

            final JsonArray continuationItems = ajaxJson.getArray("onResponseReceivedCommands")
                    .getObject(0).getObject("appendContinuationItemsAction").getArray("continuationItems");

            final JsonArray contents = continuationItems.getObject(0).getObject("itemSectionRenderer").getArray("contents");
            collectStreamsFrom(collector, contents);

            return new InfoItemsPage<>(collector, getNewNextPageFrom(continuationItems.getObject(1).getObject("continuationItemRenderer")));
        }
    }

    private void collectStreamsFrom(final InfoItemsSearchCollector collector, final JsonArray videos) throws NothingFoundException, ParsingException {
        final TimeAgoParser timeAgoParser = getTimeAgoParser();

        for (Object item : videos) {
            if (((JsonObject) item).has("backgroundPromoRenderer")) {
                throw new NothingFoundException(getTextFromObject(((JsonObject) item)
                        .getObject("backgroundPromoRenderer").getObject("bodyText")));
            } else if (((JsonObject) item).has("videoRenderer")) {
                collector.commit(new YoutubeStreamInfoItemExtractor(((JsonObject) item).getObject("videoRenderer"), timeAgoParser));
            } else if (((JsonObject) item).has("channelRenderer")) {
                collector.commit(new YoutubeChannelInfoItemExtractor(((JsonObject) item).getObject("channelRenderer")));
            } else if (((JsonObject) item).has("playlistRenderer")) {
                collector.commit(new YoutubePlaylistInfoItemExtractor(((JsonObject) item).getObject("playlistRenderer")));
            }
        }
    }

    private Page getNextPageFrom(final JsonArray continuations) throws ParsingException {
        if (isNullOrEmpty(continuations)) {
            return null;
        }

        final JsonObject nextContinuationData = continuations.getObject(0).getObject("nextContinuationData");
        final String continuation = nextContinuationData.getString("continuation");
        final String clickTrackingParams = nextContinuationData.getString("clickTrackingParams");

        return new Page(getUrl() + "&pbj=1&ctoken=" + continuation + "&continuation=" + continuation
                + "&itct=" + clickTrackingParams);
    }

    private Page getNewNextPageFrom(final JsonObject continuationItemRenderer) throws IOException, ExtractionException {
        if (isNullOrEmpty(continuationItemRenderer)) {
            return null;
        }

        final String token = continuationItemRenderer.getObject("continuationEndpoint")
                .getObject("continuationCommand").getString("token");

        final String url = "https://www.youtube.com/youtubei/v1/search?key=" + getKey();

        return new Page(url, token);
    }
}
