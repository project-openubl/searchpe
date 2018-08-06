package io.searchpe.utils;

import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

public class DateUtils {

    private DateUtils() {
        // Just utils
    }

    public static Date getNearestFutureExpirationDate(LocalTime time) {
        return getNearestFutureExpirationDate(time, Optional.empty());
    }

    public static Date getNearestFutureExpirationDate(LocalTime time, Optional<TimeZone> timeZone) {
        Calendar calendar = Calendar.getInstance();

        timeZone.ifPresent(calendar::setTimeZone);

        calendar.set(Calendar.HOUR, time.getHour());
        calendar.set(Calendar.MINUTE, time.getMinute());
        calendar.set(Calendar.SECOND, time.getSecond());

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        return calendar.getTime();
    }

    public static Date addMilliseconds(Date date, long milliseconds) {
        long differenceInMillis = date.getTime() + milliseconds;
        return new Date(differenceInMillis);
    }

}
