package org.schabi.newpipe.extractor.services.media_ccc.extractors;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public final class MediaCCCParsingHelper {
    private MediaCCCParsingHelper() { }

    public static Calendar parseDateFrom(final String textualUploadDate) throws ParsingException {
        final Date date;
        try {
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            date = sdf.parse(textualUploadDate);
        } catch (ParseException e) {
            throw new ParsingException("Could not parse date: \"" + textualUploadDate + "\"", e);
        }

        final Calendar uploadDate = Calendar.getInstance();
        uploadDate.setTime(date);
        return uploadDate;
    }

}
