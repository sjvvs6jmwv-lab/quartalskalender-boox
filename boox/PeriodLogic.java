package de.quartal.boox;

import java.time.*;
import java.time.format.DateTimeFormatter;

/** Calendar calculations independent of Android; runnable tests with a stock JDK. */
public final class PeriodLogic {
    private PeriodLogic() {}
    public static LocalDate monday(LocalDate date) {return date.minusDays(date.getDayOfWeek().getValue()-1);}
    public static LocalDate end13(LocalDate startMonday) {return monday(startMonday).plusWeeks(13).minusDays(1);}
    public static LocalDate quarterStart(LocalDate date) {return LocalDate.of(date.getYear(),((date.getMonthValue()-1)/3)*3+1,1);}
    public static LocalDate quarterEnd(LocalDate date) {return quarterStart(date).plusMonths(3).minusDays(1);}
    public static String[] googleDates(LocalDate date,String startTime,String endTime,ZoneId zone) {
        DateTimeFormatter basic=DateTimeFormatter.BASIC_ISO_DATE;
        if(startTime==null||startTime.isEmpty())return new String[]{date.format(basic),date.plusDays(1).format(basic)};
        try {
            LocalTime begin=LocalTime.parse(startTime);
            LocalTime finish=(endTime==null||endTime.isEmpty())?begin.plusHours(1):LocalTime.parse(endTime);
            ZonedDateTime first=date.atTime(begin).atZone(zone);
            ZonedDateTime last=date.atTime(finish).atZone(zone);
            if(!last.isAfter(first))last=last.plusDays(1);
            DateTimeFormatter utc=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
            return new String[]{utc.format(first.toInstant()),utc.format(last.toInstant())};
        }catch(Exception ex){return new String[]{date.format(basic),date.plusDays(1).format(basic)};}
    }
}
