package de.quartal.boox;

import android.Manifest;
import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.CalendarContract;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.*;
import java.time.temporal.IsoFields;
import java.util.*;

/** Native Android 13-week/quarter planner, compatible with local 1.5 storage and imports. */
public class MainActivity extends Activity {
  private static final int IMPORT_JSON=31, IMPORT_ICS=32, EXPORT_JSON=33, EXPORT_ICS=34, READ_PERMISSION=35;
  private static final String PREFS="quartal_native_v1", ENTRIES="entries", CALENDAR_PREFS="quartal_calendar_settings";
  private static final int INK=Color.rgb(22,26,24), MUTED=Color.rgb(73,77,75), LINE=Color.rgb(165,169,166), PAPER=Color.WHITE, RED=Color.rgb(202,24,36), TASK=Color.rgb(69,73,76);
  private final ArrayList<Entry> entries=new ArrayList<>();
  private final HashMap<LocalDate,ArrayList<Entry>> remoteDays=new HashMap<>();
  private final ArrayList<CalendarSource> availableCalendars=new ArrayList<>();
  private LocalDate startMonday, selected=LocalDate.now(), quarterAnchor=LocalDate.now();
  private LocalDate fetchedStart, fetchedEnd;
  private int view=0; // 0 rolling 13 weeks; 1 calendar quarter; 2 selected day
  private boolean agendaDayOnly=false, refreshRequired=true, created=false, firstResume=true;
  private LinearLayout content, root, header, agendaList;
  private ScrollView calendarScroll, agendaScroll;
  private boolean preserveCalendarScroll=false;
  private TextView period, selectedInfo, agendaHeading;
  private final DateTimeFormatter iso=DateTimeFormatter.ISO_LOCAL_DATE;
  private final Locale de=Locale.GERMAN;

  @Override public void onCreate(Bundle b){
    super.onCreate(b);getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    startMonday=monday(LocalDate.now());load();created=true;render();
  }
  @Override protected void onResume(){super.onResume();if(firstResume){firstResume=false;return;}if(created){refreshRequired=true;render();}}
  @Override public void onRequestPermissionsResult(int req,String[] ps,int[] grants){
    super.onRequestPermissionsResult(req,ps,grants);
    if(req==READ_PERMISSION){refreshRequired=true;render();if(canReadCalendar())calendarSourcesDialog();
      else toast("Kalenderzugriff nicht erlaubt. Lokale Termine bleiben erhalten.");}
  }
  private boolean canReadCalendar(){return checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED;}
  private LocalDate monday(LocalDate d){return PeriodLogic.monday(d);}
  private String fmt(LocalDate d,String p){return d.format(DateTimeFormatter.ofPattern(p,de));}
  private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  private GradientDrawable shape(int color,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1.3f),stroke);return g;}
  private TextView text(String s,int size,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
  private Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setTextSize(16);b.setAllCaps(false);b.setMinHeight(dp(52));b.setMinimumWidth(dp(60));b.setPadding(dp(12),dp(2),dp(12),dp(2));b.setOnClickListener(v->action.run());return b;}
  private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
  private LinearLayout col(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);return r;}
  private void addButton(LinearLayout r,String name,Runnable action){r.addView(button(name,action),new LinearLayout.LayoutParams(-2,dp(53)));}
  private void addToolbar(LinearLayout parent,LinearLayout toolbar){HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setFillViewport(false);hs.addView(toolbar);parent.addView(hs,new LinearLayout.LayoutParams(-1,dp(55)));}
  private LocalDate rangeStart(){if(view==1)return PeriodLogic.quarterStart(quarterAnchor);if(view==2)return selected;return startMonday;}
  private LocalDate rangeEnd(){if(view==1)return PeriodLogic.quarterEnd(quarterAnchor);if(view==2)return selected;return PeriodLogic.end13(startMonday);}
  private String periodLabel(){if(view==2)return fmt(selected,"EEEE, d. MMMM yyyy");if(view==1){int q=(quarterAnchor.getMonthValue()-1)/3+1;return "Q"+q+" · "+quarterAnchor.getYear();}return fmt(startMonday,"d. MMM")+" – "+fmt(rangeEnd(),"d. MMM yyyy");}
  private void render(){
    if(!created)return;
    refreshExternalIfNeeded();
    root=col();root.setBackgroundColor(PAPER);setContentView(root);
    header=col();header.setPadding(dp(12),dp(4),dp(12),dp(4));header.setBackgroundColor(PAPER);root.addView(header);
    LinearLayout brand=row();TextView title=text("Quartal",25,true,INK);brand.addView(title,new LinearLayout.LayoutParams(0,dp(50),1));brand.addView(button("＋ Eintrag",()->edit(null,selected)),new LinearLayout.LayoutParams(-2,dp(52)));header.addView(brand);
    LinearLayout nav=row();nav.addView(button("‹",()->shift(-1)),new LinearLayout.LayoutParams(dp(65),dp(53)));period=text(periodLabel(),19,true,INK);period.setGravity(Gravity.CENTER);nav.addView(period,new LinearLayout.LayoutParams(0,dp(53),1));nav.addView(button("›",()->shift(1)),new LinearLayout.LayoutParams(dp(65),dp(53)));header.addView(nav);
    LinearLayout modes=row();addButton(modes,"13 Wochen",()->{view=0;agendaDayOnly=false;render();});addButton(modes,"Quartal",()->{view=1;agendaDayOnly=false;quarterAnchor=selected;render();});addButton(modes,"Tag",()->{view=2;render();});addToolbar(header,modes);
    LinearLayout actions=row();addButton(actions,"Heute",()->{selected=LocalDate.now();startMonday=monday(selected);quarterAnchor=selected;render();});addButton(actions,"Kalender",this::calendarSources);addButton(actions,"Google",this::googleDraft);addButton(actions,"Import",this::chooseImport);addButton(actions,"Export",this::exportMenu);addToolbar(header,actions);
    selectedInfo=text("Ausgewählt: "+fmt(selected,"EEE, d. MMMM yyyy")+" · KW "+String.format(Locale.GERMAN,"%02d",selected.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)),16,true,INK);
    selectedInfo.setPadding(dp(5),dp(5),dp(5),dp(6));header.addView(selectedInfo);
    View rule=new View(this);rule.setBackgroundColor(LINE);header.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));
    // Both panes independently scroll. The 13 compact weeks fit on a 10.3-inch screen.
    int oldY=preserveCalendarScroll && calendarScroll!=null ? calendarScroll.getScrollY():0;
    preserveCalendarScroll=false;
    calendarScroll=new ScrollView(this);calendarScroll.setFillViewport(false);
    root.addView(calendarScroll,new LinearLayout.LayoutParams(-1,0,view==2?1.15f:1.85f));
    content=col();content.setPadding(dp(10),dp(6),dp(10),dp(8));calendarScroll.addView(content);
    if(view==0)renderWeeks();else if(view==1)renderQuarter();else renderDay();
    LinearLayout lower=col();lower.setBackground(shape(Color.WHITE,LINE,0));root.addView(lower,new LinearLayout.LayoutParams(-1,0,1.0f));
    LinearLayout agendaBar=row();agendaBar.setPadding(dp(11),dp(4),dp(5),dp(4));
    agendaHeading=text("",18,true,INK);agendaBar.addView(agendaHeading,new LinearLayout.LayoutParams(0,dp(53),1));
    addButton(agendaBar,"Zeitraum",()->{agendaDayOnly=false;renderAgendaOnly();});
    addButton(agendaBar,"Tag",()->{agendaDayOnly=true;renderAgendaOnly();});
    addButton(agendaBar,"＋ Termin",()->edit(null,selected));lower.addView(agendaBar);
    View line=new View(this);line.setBackgroundColor(LINE);lower.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    agendaScroll=new ScrollView(this);lower.addView(agendaScroll,new LinearLayout.LayoutParams(-1,0,1));
    agendaList=col();agendaList.setPadding(dp(11),dp(5),dp(11),dp(12));agendaScroll.addView(agendaList);
    renderAgendaOnly();
    if(oldY>0)calendarScroll.post(()->calendarScroll.scrollTo(0,oldY));
  }
  private void shift(int n){if(view==0)startMonday=startMonday.plusWeeks(n);else if(view==1)quarterAnchor=quarterAnchor.plusMonths(n*3L);else selected=selected.plusDays(n);render();}
  private boolean shadowed(Entry e,ArrayList<Entry> ext){if(!e.source.equals("android-copy")||ext==null)return false;for(Entry x:ext)if(!x.uid.isEmpty()&&x.uid.equals(e.uid))return true;return false;}
  private ArrayList<Entry> on(LocalDate d){ArrayList<Entry> a=new ArrayList<>();ArrayList<Entry> ext=remoteDays.get(d);for(Entry e:entries)if(e.date.equals(d)&&!shadowed(e,ext))a.add(e);if(ext!=null)a.addAll(ext);sortEntries(a);return a;}
  private void sortEntries(ArrayList<Entry> a){a.sort((x,y)->{int d=x.date.compareTo(y.date);if(d!=0)return d;String t=x.time.isEmpty()?"99:99":x.time,u=y.time.isEmpty()?"99:99":y.time;return t.compareTo(u);});}
  private ArrayList<Entry> inPeriod(LocalDate from,LocalDate to){ArrayList<Entry> a=new ArrayList<>();for(Entry e:entries)if(!e.date.isBefore(from)&&!e.date.isAfter(to)&&!shadowed(e,remoteDays.get(e.date)))a.add(e);for(Map.Entry<LocalDate,ArrayList<Entry>> p:remoteDays.entrySet())if(!p.getKey().isBefore(from)&&!p.getKey().isAfter(to))a.addAll(p.getValue());sortEntries(a);return a;}
  private void renderWeeks(){
    LinearLayout names=row();TextView kwHead=text("KW",15,true,MUTED);kwHead.setGravity(Gravity.CENTER);names.addView(kwHead,new LinearLayout.LayoutParams(dp(56),dp(35)));
    for(String d:new String[]{"Mo","Di","Mi","Do","Fr","Sa","So"}){TextView t=text(d,15,true,MUTED);t.setGravity(Gravity.CENTER);names.addView(t,new LinearLayout.LayoutParams(0,dp(35),1));}content.addView(names);
    for(int w=0;w<13;w++){
      LocalDate first=startMonday.plusWeeks(w);LinearLayout wr=row();wr.setGravity(Gravity.TOP);wr.setPadding(0,dp(2),0,dp(2));
      TextView kw=text(String.format(Locale.GERMAN,"%02d",first.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)),17,true,INK);kw.setGravity(Gravity.CENTER);kw.setBackground(shape(Color.rgb(243,243,243),LINE,5));
      kw.setOnClickListener(v->{startMonday=first;render();});wr.addView(kw,new LinearLayout.LayoutParams(dp(56),dp(43)));
      for(int i=0;i<7;i++)wr.addView(dayCell(first.plusDays(i),false),new LinearLayout.LayoutParams(0,dp(43),1));
      content.addView(wr);View line=new View(this);line.setBackgroundColor(LINE);content.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    TextView help=text("KW antippen: 13-Wochen-Fenster beginnt mit dieser Woche.",15,false,MUTED);help.setPadding(dp(3),dp(8),dp(3),dp(8));content.addView(help);
  }
  private View dayCell(LocalDate d,boolean compact){
    LinearLayout cell=col();boolean active=d.equals(selected);int bg=active?Color.rgb(231,234,232):(d.getDayOfWeek().getValue()>5?Color.rgb(248,248,248):PAPER);
    cell.setPadding(dp(3),dp(5),dp(2),dp(2));cell.setBackground(shape(bg,active?INK:Color.rgb(219,220,219),4));
    cell.setOnClickListener(v->{selected=d;agendaDayOnly=true;preserveCalendarScroll=true;render();});
    // Long press with the stylus jumps straight from the 13-week grid into entry.
    cell.setOnLongClickListener(v->{selected=d;edit(null,d);return true;});
    boolean compactWeek=view==0;
    LinearLayout top=row();TextView number=text(fmt(d,"d"),compact?17:18,true,d.equals(LocalDate.now())?Color.WHITE:INK);
    if(d.equals(LocalDate.now())){number.setBackground(shape(INK,0,14));number.setGravity(Gravity.CENTER);number.setMinWidth(dp(25));number.setMinHeight(dp(25));}top.addView(number);
    int events=0,tasks=0;ArrayList<Entry> day=on(d);for(Entry e:day){if(e.kind.equals("task"))tasks++;else events++;}
    if(compactWeek){
      if(events>0){View dot=new View(this);dot.setBackground(shape(RED,0,8));LinearLayout.LayoutParams dotLp=new LinearLayout.LayoutParams(dp(11),dp(11));dotLp.leftMargin=dp(3);top.addView(dot,dotLp);
        if(events>1)top.addView(text(Integer.toString(events),12,true,RED));}
      if(tasks>0)top.addView(text("■",12,true,TASK));
      cell.addView(top);return cell;
    }
    if(d.getDayOfMonth()==1){TextView month=text(fmt(d,"MMM"),12,true,MUTED);month.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);top.addView(month,new LinearLayout.LayoutParams(0,dp(28),1));}cell.addView(top);
    if(events>0){LinearLayout symbols=row();symbols.setGravity(Gravity.CENTER);View dot=new View(this);dot.setBackground(shape(RED,0,12));symbols.addView(dot,new LinearLayout.LayoutParams(dp(13),dp(13)));
      if(events>1)symbols.addView(text(" "+events,15,true,RED));cell.addView(symbols,new LinearLayout.LayoutParams(-1,dp(27)));}
    if(tasks>0){TextView mark=text("■"+(tasks>1?" "+tasks:""),16,true,TASK);mark.setGravity(Gravity.CENTER);cell.addView(mark);}
    if(!compact&&!day.isEmpty()){TextView preview=text(day.get(0).title,12,false,INK);preview.setMaxLines(1);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);cell.addView(preview);}
    return cell;
  }
  private void renderQuarter(){
    LocalDate q=rangeStart();for(int m=0;m<3;m++){
      LocalDate first=q.plusMonths(m);LinearLayout card=col();card.setPadding(dp(7),dp(5),dp(7),dp(7));card.setBackground(shape(PAPER,LINE,5));
      LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);content.addView(card,cp);
      TextView label=text(fmt(first,"MMMM yyyy"),19,true,INK);label.setPadding(dp(4),dp(4),dp(4),dp(6));card.addView(label);
      LinearLayout head=row();for(String day:new String[]{"Mo","Di","Mi","Do","Fr","Sa","So"}){TextView t=text(day,14,true,MUTED);t.setGravity(Gravity.CENTER);head.addView(t,new LinearLayout.LayoutParams(0,dp(31),1));}card.addView(head);
      int offset=first.getDayOfWeek().getValue()-1,total=first.lengthOfMonth();
      for(int n=0;n<42;n+=7){LinearLayout r=row();for(int c=0;c<7;c++){int date=n+c-offset+1;
        if(date<1||date>total){r.addView(new View(this),new LinearLayout.LayoutParams(0,dp(68),1));}
        else r.addView(dayCell(first.withDayOfMonth(date),true),new LinearLayout.LayoutParams(0,dp(68),1));
      }card.addView(r);if(n+7>=offset+total)break;}
    }
  }
  private void renderDay(){
    TextView heading=text(fmt(selected,"EEEE, d. MMMM yyyy"),22,true,INK);heading.setPadding(dp(4),dp(10),dp(4),dp(8));content.addView(heading);
    addButton(content,"＋ Neuer Eintrag an diesem Tag",()->edit(null,selected));
    addButton(content,"Neuer Termin im Android-Kalender",this::systemCalendarDraft);
    ArrayList<Entry> a=on(selected);if(a.isEmpty()){TextView t=text("Keine Termine oder Aufgaben für diesen Tag.",17,false,MUTED);t.setPadding(dp(6),dp(18),dp(6),dp(12));content.addView(t);}
    else for(Entry e:a)addAgendaItem(content,e,false);
  }
  private void renderAgendaOnly(){
    if(agendaList==null)return;agendaList.removeAllViews();boolean day=view==2||agendaDayOnly;
    LocalDate from=day?selected:rangeStart(),to=day?selected:rangeEnd();ArrayList<Entry> a=inPeriod(from,to);
    agendaHeading.setText((day?"Tag":view==1?"Quartal":"13 Wochen")+" · "+a.size()+" Einträge");
    if(a.isEmpty()){TextView none=text("Keine Einträge im angezeigten "+(day?"Tag.":"Zeitraum."),17,false,MUTED);none.setPadding(dp(4),dp(15),dp(4),dp(15));agendaList.addView(none);return;}
    for(Entry e:a)addAgendaItem(agendaList,e,true);
  }
  private void addAgendaItem(LinearLayout parent,Entry e,boolean showDate){
    LinearLayout item=row();item.setPadding(dp(10),dp(5),dp(7),dp(5));item.setBackground(shape(PAPER,LINE,5));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(6);parent.addView(item,lp);
    TextView marker=text(e.kind.equals("task")?"■":"●",22,true,e.kind.equals("task")?TASK:RED);marker.setGravity(Gravity.CENTER);item.addView(marker,new LinearLayout.LayoutParams(dp(36),dp(55)));
    LinearLayout details=col();String when=(showDate?fmt(e.date,"EEE, dd.MM")+"  ·  ":"")+(e.time.isEmpty()?"ganztägig":e.time+(e.endTime.isEmpty()?"":"–"+e.endTime));
    TextView dateLabel=text(when+(e.external?"  ·  Kalender":""),14,false,MUTED);details.addView(dateLabel);
    TextView name=text((e.done?"✓ ":"")+e.title,17,true,INK);name.setMaxLines(2);details.addView(name);
    if(!e.notes.isEmpty()){TextView notes=text(e.notes,14,false,MUTED);notes.setMaxLines(2);details.addView(notes);}
    item.addView(details,new LinearLayout.LayoutParams(0,-2,1));
    item.addView(button(e.external?"Öffnen":"Ändern",()->{if(e.external)openExternal(e);else edit(e,e.date);}),new LinearLayout.LayoutParams(-2,dp(53)));
    item.setOnClickListener(v->{selected=e.date;if(e.external)openExternal(e);else edit(e,e.date);});
  }
  private void openExternal(Entry e){try{Uri uri=ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,e.eventId);
    startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ex){toast("Für diesen Kalender ist keine passende Kalender-App verfügbar.");}}
  private void calendarSources(){if(!canReadCalendar()){requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},READ_PERMISSION);return;}
    refreshRequired=true;refreshExternalIfNeeded();calendarSourcesDialog();}
  private void calendarSourcesDialog(){
    if(availableCalendars.isEmpty()){new AlertDialog.Builder(this).setTitle("Android-Kalender")
      .setMessage("Keine über den Android-Kalenderdienst erreichbaren Kalender gefunden. Richte ggf. das Google-Konto in den Android-Systemkonten ein. BOOX Calendar Memo stellt seine eigenen Termine möglicherweise nicht über diese Schnittstelle bereit.")
      .setPositiveButton("Aktualisieren",(d,w)->{refreshRequired=true;render();}).setNegativeButton("Schließen",null).show();return;}
    String selection=getPreferencesForCalendars();String[] labels=new String[availableCalendars.size()];boolean[] checks=new boolean[labels.length];
    for(int i=0;i<labels.length;i++){CalendarSource c=availableCalendars.get(i);labels[i]=c.name+"  ·  "+c.account;checks[i]=selection.equals("*")?c.visible:calendarIsSelected(selection,c.id);}
    new AlertDialog.Builder(this).setTitle("Kalender anzeigen · synchronisierte Quellen")
      .setMultiChoiceItems(labels,checks,(dlg,idx,value)->checks[idx]=value)
      .setNegativeButton("Abbrechen",null)
       .setNeutralButton("Lokal importieren",(d,w)->{saveCalendarChoice(checks);refreshRequired=true;refreshExternalIfNeeded();importCurrentCalendarRange();render();})
      .setPositiveButton("Anzeigen",(d,w)->{saveCalendarChoice(checks);refreshRequired=true;render();}).show();
  }
  private void saveCalendarChoice(boolean[] checks){StringBuilder s=new StringBuilder();for(int i=0;i<checks.length;i++)if(checks[i]){if(s.length()>0)s.append(',');s.append(availableCalendars.get(i).id);}
    getSharedPreferences(CALENDAR_PREFS,MODE_PRIVATE).edit().putString("selected",s.toString()).apply();}
  private void importCurrentCalendarRange(){int n=0;for(Entry x:inPeriod(rangeStart(),rangeEnd())){
      if(!x.external||duplicateUid(x.uid))continue;
      Entry copy=new Entry();copy.id="copy-"+x.id;copy.uid=x.uid;copy.title=x.title;copy.date=x.date;copy.time=x.time;copy.endTime=x.endTime;
      copy.notes=x.notes;copy.kind="event";copy.source="android-copy";entries.add(copy);n++;
    }save();toast(n+" Kalendereinträge einmalig lokal übernommen. Die Originale bleiben die synchronisierte Quelle.");}
  private boolean duplicateUid(String uid){for(Entry e:entries)if(!uid.isEmpty()&&uid.equals(e.uid))return true;return false;}
  private String getPreferencesForCalendars(){return getSharedPreferences(CALENDAR_PREFS,MODE_PRIVATE).getString("selected","*");}
  private boolean calendarIsSelected(String list,long id){for(String t:list.split(","))if(t.equals(Long.toString(id)))return true;return false;}
  private void refreshExternalIfNeeded(){
    if(!canReadCalendar()){remoteDays.clear();availableCalendars.clear();return;}
    LocalDate from=rangeStart(),to=rangeEnd();
    if(!refreshRequired&&from.equals(fetchedStart)&&to.equals(fetchedEnd))return;
    remoteDays.clear();availableCalendars.clear();fetchedStart=from;fetchedEnd=to;refreshRequired=false;
    String[] calColumns={CalendarContract.Calendars._ID,CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
      CalendarContract.Calendars.ACCOUNT_NAME,CalendarContract.Calendars.VISIBLE};
    try(Cursor c=getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,calColumns,null,null,null)){
      if(c!=null)while(c.moveToNext()){CalendarSource src=new CalendarSource();src.id=c.getLong(0);src.name=c.getString(1);src.account=c.getString(2);src.visible=c.getInt(3)!=0;availableCalendars.add(src);}
    }catch(SecurityException ex){toast("Kalenderberechtigung prüfen.");return;}catch(Exception ex){toast("Kalenderliste konnte nicht gelesen werden.");return;}
    String selectedCal=getPreferencesForCalendars();
    HashSet<Long> allowed=new HashSet<>();for(CalendarSource c:availableCalendars)if(selectedCal.equals("*")?c.visible:calendarIsSelected(selectedCal,c.id))allowed.add(c.id);
    if(allowed.isEmpty())return;
    long begin=from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    long endExclusive=to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    Uri.Builder builder=CalendarContract.Instances.CONTENT_URI.buildUpon();ContentUris.appendId(builder,begin);ContentUris.appendId(builder,endExclusive);
    String[] cols={CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.CALENDAR_ID,CalendarContract.Instances.TITLE,
      CalendarContract.Instances.BEGIN,CalendarContract.Instances.END,CalendarContract.Instances.ALL_DAY,CalendarContract.Instances.DESCRIPTION};
    try(Cursor c=getContentResolver().query(builder.build(),cols,null,null,CalendarContract.Instances.BEGIN+" ASC")){
      if(c==null)return;
      int counter=0;while(c.moveToNext()&&counter<10000){long id=c.getLong(0),calendarId=c.getLong(1);if(!allowed.contains(calendarId))continue;
        long startMs=c.getLong(3),endMs=c.getLong(4);boolean allDay=c.getInt(5)!=0;
        ZoneId zone=allDay?ZoneOffset.UTC:ZoneId.systemDefault();LocalDate first=Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate();
        // End is exclusive; subtract 1ms to display correctly for multi-day entries.
        LocalDate last=Instant.ofEpochMilli(Math.max(startMs,endMs-1)).atZone(zone).toLocalDate();
        if(last.isBefore(from)||first.isAfter(to))continue;
        for(LocalDate day=first.isBefore(from)?from:first;!day.isAfter(last)&&!day.isAfter(to);day=day.plusDays(1)){
          Entry e=new Entry();e.id="android-"+id+"-"+startMs+"-"+day;e.uid=e.id;e.eventId=id;e.external=true;e.source="android";
          e.date=day;e.title=c.getString(2);if(e.title==null||e.title.isEmpty())e.title="(ohne Titel)";
          e.notes=c.getString(6);if(e.notes==null)e.notes="";
          if(!allDay&&day.equals(first))e.time=Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
          if(!allDay&&day.equals(last))e.endTime=Instant.ofEpochMilli(endMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
          if(!remoteDays.containsKey(day))remoteDays.put(day,new ArrayList<>());remoteDays.get(day).add(e);counter++;
        }
      }
    }catch(SecurityException ex){toast("Leseberechtigung für Android-Kalender fehlt.");}catch(Exception ex){toast("Android-Kalendereinträge konnten nicht geladen werden.");}
  }
  /** Launches a native system calendar editor. Its account/sync provider handles cloud synchronization. */
  private void systemCalendarDraft(){try{
    Intent i=new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI);
    long begin=selected.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    long end=selected.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,begin);i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,end);
    i.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY,true);startActivity(i);
  }catch(Exception ex){toast("Keine Android-Kalender-App zum Anlegen von Terminen gefunden.");}}
  /** System calendar entry with the text already entered in the Quartal dialog. */
  private void transferDraftToCalendar(String name,LocalDate date,String start,String finish,String note){
    try{
      Intent i=new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI);
      i.putExtra(CalendarContract.Events.TITLE,name);
      i.putExtra(CalendarContract.Events.DESCRIPTION,note);
      if(start.isEmpty()){
        i.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY,true);
        i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
      }else{
        LocalDateTime begin=date.atTime(LocalTime.parse(start));
        LocalDateTime end=date.atTime(finish.isEmpty()?LocalTime.parse(start).plusHours(1):LocalTime.parse(finish));
        if(!end.isAfter(begin))end=end.plusDays(1);
        i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,begin.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
      }
      startActivity(i);
    }catch(Exception e){toast("Kalenderformular konnte nicht geöffnet werden. Datum und Uhrzeit prüfen.");}
  }
  private void googleDraft(){
    ArrayList<Entry> day=on(selected);ArrayList<Entry> own=new ArrayList<>();for(Entry e:day)if(!e.external&&e.kind.equals("event"))own.add(e);
    if(own.isEmpty()){openGoogleTemplate(null);return;}
    String[] actions=new String[]{"Neuen Google-Termin am "+fmt(selected,"dd.MM.yyyy")+" anlegen", "Vorhandenen Quartal-Termin übernehmen", "Android-Kalender öffnen"};
    new AlertDialog.Builder(this).setTitle("Termin anlegen").setItems(actions,(dialog,index)->{
      if(index==0)openGoogleTemplate(null);
      else if(index==1&&!own.isEmpty()){String[] titles=new String[own.size()];for(int i=0;i<own.size();i++)titles[i]=own.get(i).title;
        new AlertDialog.Builder(this).setTitle("Termin auswählen").setItems(titles,(d,n)->openGoogleTemplate(own.get(n))).show();}
      else systemCalendarDraft();
    }).show();
  }
  private void openGoogleTemplate(Entry e){
    LocalDate d=e==null?selected:e.date;String start,end;
    if(e==null||e.time.isEmpty()){
      String[] dates=PeriodLogic.googleDates(d,"","",ZoneId.systemDefault());start=dates[0];end=dates[1];
    }else{
      String[] dates=PeriodLogic.googleDates(d,e.time,e.endTime,ZoneId.systemDefault());start=dates[0];end=dates[1];
    }
    String url="https://calendar.google.com/calendar/render?action=TEMPLATE&dates="+Uri.encode(start+"/"+end)
      +"&ctz="+Uri.encode(ZoneId.systemDefault().getId())
      +(e==null?"":"&text="+Uri.encode(e.title)+"&details="+Uri.encode(e.notes));
    try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ex){toast("Keine App kann das Google-Kalenderformular öffnen.");}
  }
  private void edit(Entry existing,LocalDate day){final boolean isEdit=existing!=null;Entry original=existing;LinearLayout form=col();form.setPadding(dp(16),dp(4),dp(16),dp(2));EditText title=new EditText(this);title.setTextSize(18);title.setSingleLine(true);title.setHint("Bezeichnung");title.setText(isEdit?original.title:"");form.addView(label("Titel",title));EditText date=new EditText(this);date.setTextSize(18);date.setSingleLine(true);date.setHint("JJJJ-MM-TT");date.setInputType( android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_DATE);date.setText((isEdit?original.date:day).format(iso));form.addView(label("Datum",date));EditText time=new EditText(this);time.setTextSize(18);time.setSingleLine(true);time.setHint("HH:MM oder leer für ganztägig");time.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_TIME);time.setText(isEdit?original.time:"");form.addView(label("Uhrzeit",time));EditText end=new EditText(this);end.setTextSize(18);end.setSingleLine(true);end.setHint("Ende (optional)");end.setText(isEdit?original.endTime:"");form.addView(label("Ende",end));Spinner kind=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Termin","Aufgabe"});kind.setAdapter(adapter);kind.setSelection(isEdit&&original.kind.equals("task")?1:0);form.addView(label("Art",kind));EditText notes=new EditText(this);notes.setTextSize(18);notes.setHint("Notiz (optional)");notes.setMinLines(2);notes.setText(isEdit?original.notes:"");form.addView(label("Notiz",notes));CheckBox done=new CheckBox(this);done.setText("Aufgabe erledigt");done.setTextSize(17);done.setChecked(isEdit&&original.done);form.addView(done);
    ScrollView formScroll=new ScrollView(this);formScroll.addView(form);AlertDialog dlg=new AlertDialog.Builder(this).setTitle(isEdit?"Eintrag bearbeiten":"Neuer Eintrag").setView(formScroll).setNegativeButton("Abbrechen",null).setNeutralButton(isEdit?"Löschen":"An Kalender",null).setPositiveButton("Speichern",null).create();dlg.setOnShowListener(v->{dlg.getButton(-1).setOnClickListener(x->{try{LocalDate parsed=LocalDate.parse(date.getText().toString().trim(),iso);if(title.getText().toString().trim().isEmpty())throw new Exception("Bitte einen Titel eingeben.");Entry e=isEdit?original:new Entry();e.id=isEdit?original.id:UUID.randomUUID().toString();e.title=title.getText().toString().trim();e.date=parsed;e.time=time.getText().toString().trim();e.endTime=end.getText().toString().trim();e.kind=kind.getSelectedItemPosition()==1?"task":"event";e.notes=notes.getText().toString().trim();e.done=e.kind.equals("task")&&done.isChecked();e.source="local";if(!isEdit)entries.add(e);selected=parsed;save();dlg.dismiss();render();}catch(Exception ex){date.setError("Datum JJJJ-MM-TT und Titel prüfen");}});if(!isEdit)dlg.getButton(-3).setOnClickListener(x->{try{
        LocalDate parsed=LocalDate.parse(date.getText().toString().trim(),iso);
        if(title.getText().toString().trim().isEmpty()){title.setError("Titel erforderlich");return;}
        transferDraftToCalendar(title.getText().toString().trim(),parsed,time.getText().toString().trim(),end.getText().toString().trim(),notes.getText().toString().trim());
        dlg.dismiss();
      }catch(Exception exception){date.setError("Datum oder Uhrzeit prüfen");}});
    if(isEdit)dlg.getButton(-3).setOnClickListener(x->new AlertDialog.Builder(this).setMessage("Eintrag löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(a,b)->{entries.remove(original);save();dlg.dismiss();render();}).show());});dlg.show();}
  private View label(String name,View input){LinearLayout wrap=col();TextView l=text(name,16,true,MUTED);l.setPadding(0,dp(5),0,0);wrap.addView(l);wrap.addView(input);return wrap;}
  private void exportMenu(){new AlertDialog.Builder(this).setTitle("Daten exportieren").setItems(new String[]{"JSON-Sicherung","ICS-Kalenderdatei"},(d,w)->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(w==0?"application/json":"text/calendar");i.putExtra(Intent.EXTRA_TITLE,w==0?"Quartal-Sicherung.json":"Quartal-Termine.ics");startActivityForResult(i,w==0?EXPORT_JSON:EXPORT_ICS);}).show();}
  private void chooseImport(){new AlertDialog.Builder(this).setTitle("Datei importieren").setItems(new String[]{"JSON-Sicherung","ICS-Kalenderdatei"},(d,w)->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(w==0?"application/json":"text/calendar");startActivityForResult(i,w==0?IMPORT_JSON:IMPORT_ICS);}).show();}
  @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;try{Uri u=data.getData();if(req==IMPORT_JSON||req==IMPORT_ICS){String content=read(u);int n=req==IMPORT_JSON?importJson(content):importIcs(content);save();render();new AlertDialog.Builder(this).setMessage(n+" neue Einträge übernommen. Vorhandene Einträge und Dubletten blieben erhalten.").setPositiveButton("OK",null).show();}else{String out=req==EXPORT_JSON?exportJson():exportIcs();try(OutputStream os=getContentResolver().openOutputStream(data.getData())){os.write(out.getBytes(StandardCharsets.UTF_8));}Toast.makeText(this,"Export gespeichert",Toast.LENGTH_LONG).show();}}catch(Exception e){new AlertDialog.Builder(this).setMessage("Datei konnte nicht verarbeitet werden: "+e.getMessage()).setPositiveButton("OK",null).show();}}
  private String read(Uri u)throws Exception{try(InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);return b.toString("UTF-8");}}
  private JSONObject toJson(Entry e)throws JSONException{JSONObject o=new JSONObject();o.put("id",e.id);o.put("title",e.title);o.put("date",e.date.format(iso));o.put("time",e.time);o.put("endTime",e.endTime);o.put("kind",e.kind);o.put("notes",e.notes);o.put("done",e.done);o.put("source",e.source);if(!e.uid.isEmpty())o.put("uid",e.uid);return o;}
  private String exportJson()throws JSONException{JSONArray a=new JSONArray();for(Entry e:entries)a.put(toJson(e));JSONObject root=new JSONObject();root.put("format","quartal-v1");root.put("exported",Instant.now().toString());root.put("entries",a);return root.toString(2);}
  private int importJson(String json)throws Exception{Object parsed=new JSONTokener(json).nextValue();JSONArray a=parsed instanceof JSONArray?(JSONArray)parsed:((JSONObject)parsed).optJSONArray("entries");if(a==null)throw new Exception("JSON-Datei enthält keine entries.");int added=0;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;Entry e=new Entry();e.id=o.optString("id",UUID.randomUUID().toString());e.title=o.optString("title","").trim();e.date=LocalDate.parse(o.getString("date"),iso);if(e.title.isEmpty())continue;e.time=o.optString("time","");e.endTime=o.optString("endTime","");e.kind=o.optString("kind","event").equals("task")?"task":"event";e.notes=o.optString("notes","");e.done=o.optBoolean("done",false);e.source=o.optString("source","local");e.uid=o.optString("uid","");if(!duplicate(e)){entries.add(e);added++;}}return added;}
  private boolean duplicate(Entry e){for(Entry x:entries)if(x.id.equals(e.id)||(!e.uid.isEmpty()&&x.uid.equals(e.uid)))return true;return false;}
  private int importIcs(String raw){String unfolded=raw.replace("\r\n","\n").replace('\r','\n').replaceAll("\n[ \\t]","" );String[] lines=unfolded.split("\n");Entry current=null;int added=0;boolean task=false;for(String line:lines){if(line.equals("BEGIN:VEVENT")||line.equals("BEGIN:VTODO")){current=new Entry();task=line.endsWith("VTODO");current.kind=task?"task":"event";current.source="ics";continue;}if(current==null)continue;if(line.equals("END:VEVENT")||line.equals("END:VTODO")){if(current.title!=null&&!current.title.isEmpty()&&current.date!=null&&!duplicate(current)){entries.add(current);added++;}current=null;continue;}int p=line.indexOf(':');if(p<0)continue;String key=line.substring(0,p).split(";",2)[0].toUpperCase(Locale.ROOT),value=line.substring(p+1);if(key.equals("SUMMARY"))current.title=unescape(value);else if(key.equals("DESCRIPTION"))current.notes=unescape(value);else if(key.equals("UID")){current.uid=value;current.id="ics-"+value;}else if(key.equals("STATUS"))current.done=value.equalsIgnoreCase("COMPLETED");else if(key.equals("DTSTART")||key.equals("DUE")){try{String v=value;if(v.length()>=8){current.date=LocalDate.parse(v.substring(0,8),DateTimeFormatter.BASIC_ISO_DATE);if(v.length()>=13&&v.charAt(8)=='T')current.time=v.substring(9,11)+":"+v.substring(11,13);}}catch(Exception ignored){}}}return added;}
  private String unescape(String s){return s.replace("\\n","\n").replace("\\,",",").replace("\\;",";").replace("\\\\","\\");}
  private String escape(String s){return s.replace("\\","\\\\").replace("\n","\\n").replace(",","\\,").replace(";","\\;");}
  private String exportIcs(){StringBuilder b=new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Quartal Native//DE\r\nCALSCALE:GREGORIAN\r\n");for(Entry e:entries){boolean task=e.kind.equals("task");b.append(task?"BEGIN:VTODO\r\n":"BEGIN:VEVENT\r\n").append("UID:").append(escape(e.uid.isEmpty()?e.id+"@quartal-local":e.uid)).append("\r\nSUMMARY:").append(escape(e.title)).append("\r\n");if(!e.notes.isEmpty())b.append("DESCRIPTION:").append(escape(e.notes)).append("\r\n");if(task&&e.done)b.append("STATUS:COMPLETED\r\n");if(e.time.isEmpty())b.append(task?"DUE;VALUE=DATE:":"DTSTART;VALUE=DATE:").append(e.date.format(DateTimeFormatter.BASIC_ISO_DATE)).append("\r\n");else b.append(task?"DUE:":"DTSTART:").append(e.date.format(DateTimeFormatter.BASIC_ISO_DATE)).append("T").append(e.time.replace(":","")).append("00\r\n");b.append(task?"END:VTODO\r\n":"END:VEVENT\r\n");}return b.append("END:VCALENDAR\r\n").toString();}
  private void load(){String raw=getSharedPreferences(PREFS,MODE_PRIVATE).getString(ENTRIES,"[]");try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Entry e=new Entry();e.id=o.optString("id",UUID.randomUUID().toString());e.title=o.optString("title","");e.date=LocalDate.parse(o.getString("date"),iso);e.time=o.optString("time","");e.endTime=o.optString("endTime","");e.kind=o.optString("kind","event");e.notes=o.optString("notes","");e.done=o.optBoolean("done",false);e.source=o.optString("source","local");e.uid=o.optString("uid","");entries.add(e);}}catch(Exception ex){Toast.makeText(this,"Lokale Daten konnten nicht gelesen werden",Toast.LENGTH_LONG).show();}}
  private void save(){JSONArray a=new JSONArray();try{for(Entry e:entries)a.put(toJson(e));}catch(Exception ignored){}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(ENTRIES,a.toString()).apply();}
  private static class CalendarSource{long id;String name="",account="";boolean visible;}
  private static class Entry{long eventId=-1;boolean external=false;String id=UUID.randomUUID().toString(),title="",time="",endTime="",kind="event",notes="",source="local",uid="";LocalDate date;boolean done=false;}
}
