package ee.ajavott;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class PeaEkraan extends ActionBarActivity {

    public static String SELECTED_KP;
    public static CharSequence SELECTED_KP_TEXT;
    public static List<TimedBoat> POPUP_BOATS = new Vector<>();
    public static boolean DISK_FULL_ERROR_STATE = false;
    public static List<TimedBoat> SENT_TIMES = new ArrayList<>();
    public static List<TimedBoat> UNSENT_TIMES = new ArrayList<>();
    public static int BOAT_COUNT = 0;

    public static final Object LOCK = new Object();
    public static final String SERVER_URL = "http://www.tyritori.ee/tulemused/vv.php";
    //private static final String SERVER_URL = "http://www.matkahunt.ee/tulemused/vv.php";

    private UnsentTimesThread WORKER_THREAD;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(ee.ajavott.R.layout.activity_pea_ekraan);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if(BOAT_COUNT != 0) {
            TextView boatCountView = (TextView) findViewById(ee.ajavott.R.id.boatCount);
            boatCountView.setText(String.valueOf(BOAT_COUNT));
            TextView kpTitleTextView = (TextView) findViewById(ee.ajavott.R.id.kpTitle);
            kpTitleTextView.setText(SELECTED_KP_TEXT);
        } else {
            loadDataFromLogs();
        }

        updateSentTimesView();
        updateUnsentTimesView();

        createAddBoatButton();

    }

    private void loadDataFromLogs() {
        if(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).exists()) {

            File filenameSent;
            File filenameUnsent;

            filenameSent = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS) + File.separator + "ajavott-sent.txt");
            filenameUnsent = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS) + File.separator + "ajavott-unsent.txt");

            FileInputStream inputStream;
            try {

                inputStream = new FileInputStream(filenameSent);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while(true) {
                    line = bufferedReader.readLine();
                    if(line != null && line.contains(";")) {
                        String[] logLine = line.split(";");
                        TimedBoat sentBoat = new TimedBoat(logLine[0], logLine[1], logLine[2]);
                        SENT_TIMES.add(sentBoat);
                    } else {
                        break;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            try {

                inputStream = new FileInputStream(filenameUnsent);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while(true) {
                    line = bufferedReader.readLine();
                    if(line != null && line.contains(";")) {
                        String[] logLine = line.split(";");
                        TimedBoat unsentBoat = new TimedBoat(logLine[0], logLine[1], logLine[2]);
                        UNSENT_TIMES.add(unsentBoat);
                    } else {
                        break;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            for(int i = 0; i < UNSENT_TIMES.size(); i++) {
                try {
                    if(SENT_TIMES.size() > 0) {
                        SENT_TIMES.remove(SENT_TIMES.size()-1);
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }

            BOAT_COUNT = SENT_TIMES.size() + UNSENT_TIMES.size();
            TextView boatCountView = (TextView) findViewById(ee.ajavott.R.id.boatCount);
            boatCountView.setText(String.valueOf(BOAT_COUNT));

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        createUnsentTimesHandlerThread();
    }

    @Override
    protected void onPause() {
        super.onPause();
        WORKER_THREAD.close();
        WORKER_THREAD = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void createUnsentTimesHandlerThread() {
        System.out.println("Starting up worker thread!");
        WORKER_THREAD = new UnsentTimesThread();
        WORKER_THREAD.start();
    }

    private class UnsentTimesThread extends Thread {

        private boolean workerThreadRunning = false;

        @Override
        public void run() {

            workerThreadRunning = true;

            while(workerThreadRunning) {

                try {
                    System.out.println("Sleeping 10 seconds");
                    //Thread.sleep((long)10000.0f);
                    TimeUnit.SECONDS.sleep((long)10.0f);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }

                int timesToSend = 0;
                synchronized (LOCK) {
                    timesToSend = UNSENT_TIMES.size();
                }

                boolean timesSent = false;
                while(timesToSend > 0) {

                    System.out.println(timesToSend + " unsent times in queue, starting to send..");
                    timesToSend -= 1;

                    TimedBoat unsentBoat;
                    synchronized (LOCK) {
                        try {
                            unsentBoat = UNSENT_TIMES.get(0);
                        } catch(IndexOutOfBoundsException e) {
                            unsentBoat = null;
                        }
                    }

                    if(unsentBoat != null) {

                        final HttpClient httpClient = new DefaultHttpClient();
                        // vv.php?kp=%s&aeg=%s&paat=%s", xkp, aeg, paat

                        String cancelledGetParameter;
                        if(unsentBoat.isCancelled()) {
                            cancelledGetParameter = "&lopp=end";
                        } else {
                            cancelledGetParameter = "";
                        }

                        final HttpGet getRequest = new HttpGet(SERVER_URL +
                                "?kp=" + unsentBoat.getSelectedKP() +
                                "&aeg=" + unsentBoat.getTime() +
                                "&paat=" + unsentBoat.getBoat() +
                                cancelledGetParameter
                        );

                        System.out.println("Executing query: " + SERVER_URL +
                                "?kp=" + unsentBoat.getSelectedKP() +
                                "&aeg=" + unsentBoat.getTime() +
                                "&paat=" + unsentBoat.getBoat() +
                                cancelledGetParameter);

                        HttpResponse response = null;
                        try {

                            response = httpClient.execute(getRequest);

                            try {

                                InputStream is = response.getEntity().getContent();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                                StringBuilder str = new StringBuilder();
                                String line = null;
                                while((line = reader.readLine()) != null){
                                    str.append(line);
                                }
                                is.close();

                                String responseString = "";
                                if(str.length() > 0) {
                                    responseString = str.toString().substring(0, str.length());
                                    System.out.println("Response: " + str.toString());
                                } else {
                                    System.out.println("ERROR: Empty response received");
                                }

                                if(responseString.startsWith("SAVED")) {

                                    timesSent = true;
                                    synchronized (LOCK) {
                                        UNSENT_TIMES.remove(unsentBoat);
                                    }

                                    System.out.println("Removed unsent time from queue: "
                                            + unsentBoat.getBoat() + " " + unsentBoat.getTime());

                                    synchronized (LOCK) {
                                        SENT_TIMES.add(unsentBoat);
                                    }

                                    System.out.println("SUCCESS: Boat sent successfully: "
                                            + unsentBoat.getBoat() + " "
                                            + unsentBoat.getTime() + " "
                                            + unsentBoat.getSelectedKP());

                                    if(DISK_FULL_ERROR_STATE) {
                                        DISK_FULL_ERROR_STATE = false;
                                    }

                                } else if(responseString.startsWith("NO_NR")) {
                                    timesSent = true;
                                    handleBadBoatNumber(unsentBoat, "NO_NR");
                                } else if(responseString.startsWith("EXIST")) {
                                    timesSent = true;
                                    handleBadBoatNumber(unsentBoat, "EXIST");
                                } else if(responseString.startsWith("D_FUL")) {

                                    System.out.println("ERROR: Disk is full");

                                    if(DISK_FULL_ERROR_STATE == false) {

                                        DISK_FULL_ERROR_STATE = true;

                                        if(!isFinishing()) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    AlertDialog.Builder kpAlertBuilder = new AlertDialog.Builder(PeaEkraan.this);
                                                    kpAlertBuilder.setMessage("ERROR: Disk is full, call Andi!!!");
                                                    kpAlertBuilder.setCancelable(true);
                                                    kpAlertBuilder.setPositiveButton("Not OK",
                                                            new DialogInterface.OnClickListener() {
                                                                public void onClick(DialogInterface dialog, int id) {
                                                                    dialog.cancel();
                                                                }
                                                            });

                                                    AlertDialog kpAlert = kpAlertBuilder.create();
                                                    kpAlert.show();

                                                }
                                            });
                                        }

                                    }

                                    try {
                                        System.out.println("Sleeping 10 seconds");
                                        //Thread.sleep((long)10000.0f);
                                        TimeUnit.SECONDS.sleep((long)10.0f);
                                    } catch(InterruptedException e) {
                                        e.printStackTrace();
                                    }

                                } else {
                                    System.out.println("Sending failed - wrong response received: " + str);
                                    try {
                                        System.out.println("Sleeping 10 seconds");
                                        //Thread.sleep((long)10000.0f);
                                        TimeUnit.SECONDS.sleep((long)10.0f);
                                    } catch(InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }

                            } catch(IOException e) {
                                e.printStackTrace();
                            }



                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    } else {
                        System.out.println("No boats in the send queue");
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            System.out.println("Updating views..");

                            updateSentTimesView();
                            updateUnsentTimesView();
                        }
                    });

                }

                clearUnsentTimes(timesSent);

            }

        }

        private void clearUnsentTimes(boolean timesSent) {
            if(timesSent) {
                synchronized (LOCK) {
                    if(UNSENT_TIMES.size() == 0) {

                        File filenameUnsent;
                        filenameUnsent = new File(
                                Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS) + File.separator + "ajavott-unsent.txt");
                        System.out.println(filenameUnsent.getAbsolutePath());
                        FileOutputStream outputStream;

                        try {
                            outputStream = new FileOutputStream(filenameUnsent);
                            //outputStream.write(0);
                            outputStream.flush();
                            outputStream.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        }

        public void close() {
            workerThreadRunning = false;
        }

        private void handleBadBoatNumber(final TimedBoat unsentBoat, final String errorText) {

            if(errorText.startsWith("NO_NR")) {
                System.out.println("ERROR: Boat does not exist, removing from queue");
            }
            else if(errorText.startsWith("EXIST")) {
                System.out.println("ERROR: Boat nr already exist for checkpoint, removing from queue");
            }

            synchronized (LOCK) {

                unsentBoat.setIsBadBoatNumber();
                POPUP_BOATS.add(unsentBoat);

                UNSENT_TIMES.remove(unsentBoat);
                SENT_TIMES.add(unsentBoat);

                if(!isFinishing()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                AlertDialog.Builder kpAlertBuilder = new AlertDialog.Builder(PeaEkraan.this);
                                TimedBoat popupBoat = POPUP_BOATS.remove(0);
                                if (errorText.startsWith("NO_NR")) {
                                    kpAlertBuilder.setMessage("TUNDMATU PAADI NUMBER: " + popupBoat);
                                } else if (errorText.startsWith("EXIST")) {
                                    kpAlertBuilder.setMessage("PAADI NUMBER JUBA SISESTATUD: " + popupBoat);
                                }
                                kpAlertBuilder.setCancelable(true);
                                kpAlertBuilder.setPositiveButton("OK",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.cancel();
                                            }
                                        });

                                AlertDialog kpAlert = kpAlertBuilder.create();
                                kpAlert.show();
                            }
                            catch(Exception e) { e.printStackTrace(); }
                        }
                    });
                }

            }
        }

    }

    private void createAddBoatButton() {

        final Button addBoatButton = (Button) findViewById(ee.ajavott.R.id.addBoatButton);
        addBoatButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                if(SELECTED_KP != null) {

                    EditText boatNumberInput = (EditText) findViewById(ee.ajavott.R.id.boatNumberInput);
                    final String boat = boatNumberInput.getText().toString();

                    if(boat != null && !boat.equals("")) {

                        final Calendar calendar = Calendar.getInstance();
                        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss,S");
                        final String boatTime = sdf.format(calendar.getTime());

                        TimedBoat timedBoat = new TimedBoat(boat, boatTime, SELECTED_KP);

                        CheckBox cancelledCheckbox = (CheckBox) findViewById(ee.ajavott.R.id.cancelled);
                        boolean cancelled = cancelledCheckbox.isChecked();
                        timedBoat.setIsCancelled(cancelled);
                        cancelledCheckbox.setChecked(false);

                        System.out.println("Timed boat: " + timedBoat.toString());

                        int unsentTimesSize = 0;
                        synchronized (LOCK) {

                            UNSENT_TIMES.add(timedBoat);
                            BOAT_COUNT += 1;
                            TextView boatCountView = (TextView) findViewById(ee.ajavott.R.id.boatCount);
                            boatCountView.setText(String.valueOf(BOAT_COUNT));

                            File filename;
                            File filenameUnsent;
                            if(!Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).exists()) {
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).mkdirs();
                            }
                            filename = new File(
                                    Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DOWNLOADS) + File.separator + "ajavott-sent.txt");
                            filenameUnsent = new File(
                                    Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DOWNLOADS) + File.separator + "ajavott-unsent.txt");

                            System.out.println(filename.getAbsolutePath());
                            String string = timedBoat.getBoat()
                                    + ";" + timedBoat.getTime() + ";" + timedBoat.getSelectedKP() + "\n";
                            FileOutputStream outputStream;

                            try {
                                outputStream = new FileOutputStream(filename, true);
                                outputStream.write(string.getBytes());
                                outputStream.flush();
                                outputStream.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            try {
                                outputStream = new FileOutputStream(filenameUnsent, true);
                                outputStream.write(string.getBytes());
                                outputStream.flush();
                                outputStream.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        }

                        boatNumberInput.setText("");
                        updateUnsentTimesView();

                    }

                } else {

                    AlertDialog.Builder kpAlertBuilder = new AlertDialog.Builder(PeaEkraan.this);
                    kpAlertBuilder.setMessage("Palun vali KP vajutades ülevalt menüü nuppu");
                    kpAlertBuilder.setCancelable(true);
                    kpAlertBuilder.setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });

                    AlertDialog kpAlert = kpAlertBuilder.create();
                    kpAlert.show();

                }

            }
        });

    }

    private synchronized void updateSentTimesView() {

        int sentTimesSize;
        synchronized (LOCK) {
            sentTimesSize = SENT_TIMES.size();
        }

        TextView sentTimeTextView = null;
        TimedBoat timedBoat = null;

        for(int i = 1; i <= 12; i++) {

            try {
                synchronized (LOCK) {
                    int index = sentTimesSize - i;
                    timedBoat = SENT_TIMES.get(index);
                }
            } catch(IndexOutOfBoundsException e) {
                timedBoat = null;
            }

            switch (i) {
                case 1:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent1);
                    break;
                case 2:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent2);
                    break;
                case 3:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent3);
                    break;
                case 4:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent4);
                    break;
                case 5:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent5);
                    break;
                case 6:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent6);
                    break;
                case 7:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent7);
                    break;
                case 8:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent8);
                    break;
                case 9:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent9);
                    break;
                case 10:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent10);
                    break;
                case 11:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent11);
                    break;
                case 12:
                    sentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.sent12);
                    break;
            }

            if(timedBoat != null) {
                sentTimeTextView.setText(timedBoat.getBoat() + " " + timedBoat.getTime() + " " + timedBoat.getSelectedKP());
                if(timedBoat.isBadBoatNumber()) {
                    sentTimeTextView.setTextColor(Color.parseColor("#ff0000"));
                } else {
                    sentTimeTextView.setTextColor(Color.parseColor("#000000"));
                }
            } else {
                sentTimeTextView.setText("-");
                sentTimeTextView.setTextColor(Color.parseColor("#000000"));
            }

        }

    }

    private synchronized void updateUnsentTimesView() {

        int unsentTimesSize;
        synchronized (LOCK) {
            unsentTimesSize = UNSENT_TIMES.size();
        }

        TextView unsentTimeTextView = null;
        TimedBoat timedBoat = null;
        for(int i = 1; i <= 3; i++) {

            try {
                synchronized (LOCK) {
                    int index = unsentTimesSize - i;
                    timedBoat = UNSENT_TIMES.get(index);
                }
            } catch(IndexOutOfBoundsException e) {
                timedBoat = null;
            }

            switch (i) {
                case 1:
                    unsentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.unsent1);
                    break;
                case 2:
                    unsentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.unsent2);
                    break;
                case 3:
                    unsentTimeTextView = (TextView) findViewById(ee.ajavott.R.id.unsent3);
                    break;
            }

            if(timedBoat != null) {
                unsentTimeTextView.setText(timedBoat.getBoat() + " " + timedBoat.getTime() + " " + timedBoat.getSelectedKP());
            } else {
                unsentTimeTextView.setText("-");
            }

        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(ee.ajavott.R.menu.menu_pea_ekraan, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        String kpKood = item.getTitle().toString().substring(0, 4);
        CharSequence kpTekst = item.getTitle();

        if(kpTekst.equals("RESET")) {
            AlertDialog.Builder kpAlertBuilder = new AlertDialog.Builder(PeaEkraan.this);
            kpAlertBuilder.setMessage("Oled sa kindel, et soovid logi arhiveerida?");
            kpAlertBuilder.setCancelable(true);
            kpAlertBuilder.setPositiveButton("Jah",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss");
                            String dateString = sdf.format(new Date());

                            synchronized (LOCK) {
                                if(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).exists()) {

                                    File filenameSent = new File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + "ajavott-sent.txt");

                                    String kp;
                                    if(SELECTED_KP == null) {
                                        kp = "NA";
                                    } else {
                                        kp = SELECTED_KP;
                                    }

                                    File filenameSentNew = new File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                                    + File.separator + "ajavott-sent-"
                                                    + kp
                                                    + "-"
                                                    + dateString
                                                    + ".txt");

                                    if(filenameSent.exists()) {
                                        filenameSent.renameTo(filenameSentNew);
                                    }

                                    SELECTED_KP = null;
                                    SELECTED_KP_TEXT = "";
                                    TextView kpTitleTextView = (TextView) findViewById(ee.ajavott.R.id.kpTitle);
                                    kpTitleTextView.setText(SELECTED_KP_TEXT);

                                    BOAT_COUNT = UNSENT_TIMES.size();
                                    TextView boatCountView = (TextView) findViewById(ee.ajavott.R.id.boatCount);
                                    boatCountView.setText(String.valueOf(BOAT_COUNT));

                                    SENT_TIMES.clear();
                                    updateSentTimesView();
                                    updateUnsentTimesView();

                                }
                            }

                            dialog.cancel();
                        }
                    });
            kpAlertBuilder.setNegativeButton("Ei",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            AlertDialog kpAlert = kpAlertBuilder.create();
            kpAlert.show();
        } else {
            SELECTED_KP = kpKood;
            SELECTED_KP_TEXT = kpTekst;
            System.out.println("Valitud KP: " + SELECTED_KP_TEXT);
            System.out.println("Valitud KP kood: " + SELECTED_KP);
            TextView kpTitleTextView = (TextView) findViewById(ee.ajavott.R.id.kpTitle);
            kpTitleTextView.setText(SELECTED_KP_TEXT);
        }

        return super.onOptionsItemSelected(item);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
