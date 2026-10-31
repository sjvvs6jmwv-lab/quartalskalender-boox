import de.quartal.boox.PeriodLogic;
import java.time.*;
import java.time.temporal.IsoFields;
import java.util.Arrays;

public class PeriodLogicTest {
  private static void eq(Object actual,Object expected){if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
  public static void main(String[] args){
    eq(PeriodLogic.monday(LocalDate.of(2026,10,31)),LocalDate.of(2026,10,26));
    eq(PeriodLogic.end13(LocalDate.of(2026,10,26)),LocalDate.of(2027,1,24));
    eq(PeriodLogic.monday(LocalDate.of(2027,1,1)),LocalDate.of(2026,12,28));
    eq(PeriodLogic.monday(LocalDate.of(2027,1,1)).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),53);
    eq(PeriodLogic.quarterStart(LocalDate.of(2026,10,31)),LocalDate.of(2026,10,1));
    eq(PeriodLogic.quarterEnd(LocalDate.of(2026,11,25)),LocalDate.of(2026,12,31));
    eq(PeriodLogic.quarterEnd(LocalDate.of(2024,2,29)),LocalDate.of(2024,3,31));
    eq(Arrays.toString(PeriodLogic.googleDates(LocalDate.of(2026,10,31),"","",ZoneId.of("Europe/Berlin"))),"[20261031, 20261101]");
    eq(Arrays.toString(PeriodLogic.googleDates(LocalDate.of(2026,10,31),"10:00","11:00",ZoneId.of("Europe/Berlin"))),"[20261031T090000Z, 20261031T100000Z]");
    eq(Arrays.toString(PeriodLogic.googleDates(LocalDate.of(2026,10,31),"23:30","00:30",ZoneId.of("Europe/Berlin"))),"[20261031T223000Z, 20261031T233000Z]");
    System.out.println("10 Prüfungen der Quartals-, Wochen- und Google-Terminlogik erfolgreich.");
  }
}
