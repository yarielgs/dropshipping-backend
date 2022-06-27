package uy.com.pepeganga.business.common.utils.date;

import org.joda.time.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class DateTimeUtilsBss {

    private  DateTimeUtilsBss() {
        // Do nothing
    }

    // Suma o resta las horas recibidos a la fecha
    public static long calculateDateTime(int horas) {
        Calendar calendar = Calendar.getInstance();

        calendar.getTimeZone(); // Obtengo fecha actual

        calendar.add(Calendar.HOUR, horas);  // numero de horas a añadir, o restar en caso de horas<0

        return calendar.getTimeInMillis(); // Devuelve el objeto Date con las nuevas horas añadidas
    }

    public static int convertFromSecondToHour (int seconds) {
        return (seconds / 60) / 60;
    }

    public static Date convertFromLocalDateToDate (java.time.LocalDate localDate) {
        //default time zone
        ZoneId defaultZoneId = ZoneId.systemDefault();

        //local date + atStartOfDay() + default time zone + toInstant() = Date
        return Date.from(localDate.atStartOfDay(defaultZoneId).toInstant());
    }

    public static boolean isExpiredToken(long expirationDate) {
        if( Calendar.getInstance().getTimeInMillis() > expirationDate) {
            return true;
        }

        return false;
    }

    public static DateTime getDateTimeAtCurrentTime(){
        return DateTime.now();
    }

    public static long plusCurrentTimeMilleSeconds(long plus, DateTimePlusType plusType){
        long result;
            switch (plusType) {
                case HOUR:
                    result = DateTimeUtilsBss.getDateTimeAtCurrentTime().getMillis() + plus * 24 *  60  * 1000 ;
                    break;
                case MINUTE:
                    result = DateTimeUtilsBss.getDateTimeAtCurrentTime().getMillis() + plus * 60  * 1000;
                    break;
                case SECOND:
                    result = DateTimeUtilsBss.getDateTimeAtCurrentTime().getMillis() + plus * 1000;
                    break;
                default:
                    result = DateTimeUtilsBss.getDateTimeAtCurrentTime().getMillis();
                    break;
            }
            return  result;
    }


    public static Duration getDurationOfMilleSeconds(long start, long end){
        Duration duration = new Duration(start, end);
        Instant.now().plus(duration);
        return duration;
    }

    public static Long getLongDateTimeAtCurrentTime(){
        return Long.parseLong(String.format("%d%s%s", DateTimeUtilsBss.getDateTimeAtCurrentTime().getYear(),
                helperZeroBeforeMonthOrDay(DateTimeUtilsBss.getDateTimeAtCurrentTime().getMonthOfYear()) ,
                helperZeroBeforeMonthOrDay(DateTimeUtilsBss.getDateTimeAtCurrentTime().getDayOfMonth())));
    }


    public static String helperZeroBeforeMonthOrDay(int number){
        return number / 10 >= 1 ? String.valueOf(number) : "0".concat(String.valueOf(number));
    }

    public static XMLGregorianCalendar convertToXMLGregorianCalendar(Long date) throws DatatypeConfigurationException {
        // to Gregorian Calendar
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTimeInMillis(date);

        // to XML Gregorian Calendar
        XMLGregorianCalendar xc = DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
        return xc;
    }

}

