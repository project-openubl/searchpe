package io.searchpe.utils;

import org.junit.Assert;
import org.junit.Test;

import java.time.*;
import java.util.Date;

public class DateUtilsTest {

    /**
     * Should add milliseconds
     * {@link io.searchpe.utils.DateUtils#getNextDate(ZonedDateTime, LocalTime)}
     */
    @Test
    public void test_getNextDate() {
        LocalDateTime currentDate = LocalDateTime.of(2018, 9, 30, 12, 20, 00);
        ZonedDateTime referenceDate = ZonedDateTime.of(currentDate, ZoneId.of("America/Lima"));

        // When time has already passed
        LocalTime time = LocalTime.of(8, 30, 0);

        ZonedDateTime result = DateUtils.getNextDate(referenceDate, time);
        Assert.assertEquals(2018, result.getYear());
        Assert.assertEquals(Month.OCTOBER, result.getMonth());
        Assert.assertEquals(1, result.getDayOfMonth());
        Assert.assertEquals(8, result.getHour());
        Assert.assertEquals(30, result.getMinute());
        Assert.assertEquals(0, result.getSecond());


        // When time is in the future
        time = LocalTime.of(18, 30, 0);

        result = DateUtils.getNextDate(referenceDate, time);
        Assert.assertEquals(2018, result.getYear());
        Assert.assertEquals(Month.SEPTEMBER, result.getMonth());
        Assert.assertEquals(30, result.getDayOfMonth());
        Assert.assertEquals(18, result.getHour());
        Assert.assertEquals(30, result.getMinute());
        Assert.assertEquals(0, result.getSecond());
    }

    /**
     * Should add milliseconds
     * {@link io.searchpe.utils.DateUtils#addMilliseconds(Date, long)}
     */
    @Test
    public void test_addMilliseconds() {
        Date date1 = new Date();
        Date date2 = DateUtils.addMilliseconds(date1, 100L);
        Assert.assertEquals(-100L, date1.getTime() - date2.getTime());

        date2 = DateUtils.addMilliseconds(date1, -100L);
        Assert.assertEquals(100L, date1.getTime() - date2.getTime());
    }

}