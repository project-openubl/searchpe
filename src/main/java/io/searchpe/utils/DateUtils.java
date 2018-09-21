package io.searchpe.utils;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Date;

public class DateUtils {

    private DateUtils() {
        // Just utils
    }

    /**
     * Return the nearest Date at some time
     *
     * @param zonedDateTime the date reference
     * @param localTime         the time HH:MM:SS to use on calculation
     */
    public static ZonedDateTime getNextDate(ZonedDateTime zonedDateTime, LocalTime localTime) {
        ZonedDateTime futureDate = zonedDateTime
                .withHour(localTime.getHour())
                .withMinute(localTime.getMinute())
                .withSecond(localTime.getSecond())
                .withNano(localTime.getNano());

        if (futureDate.compareTo(zonedDateTime) > 0) {
            return futureDate;
        } else {
            return futureDate.plusDays(1);
        }
    }

    /**
     * Add milliseconds to a java.util.Date and return a new java.util.Date instance
     *
     * @param date         original date
     * @param milliseconds milliseconds to add
     */
    public static Date addMilliseconds(Date date, long milliseconds) {
        long differenceInMillis = date.getTime() + milliseconds;
        return new Date(differenceInMillis);
    }

}
