package cu.axel.smartdock.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import cu.axel.smartdock.R;
import cu.axel.smartdock.models.App;
import cu.axel.smartdock.utils.AppUtils;
import cu.axel.smartdock.utils.DeviceUtils;
import cu.axel.smartdock.utils.Utils;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.EditText;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import android.content.pm.ShortcutInfo;
import cu.axel.smartdock.utils.DeepShortcutManager;
import android.widget.Toast;
import cu.axel.smartdock.icons.IconParserUtilities;

public class LauncherActivity extends Activity {
	private LinearLayout backgroundLayout;
    private Button serviceBtn;
    private String state;
    private GridView appsGv;
    private EditText notesEt;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_launcher);
		backgroundLayout = findViewById(R.id.ll_background);
        serviceBtn = findViewById(R.id.service_btn);
        appsGv = findViewById(R.id.desktop_apps_gv);
        notesEt = findViewById(R.id.notes_et);

        serviceBtn.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    startActivity(new Intent(LauncherActivity.this, MainActivity.class));
                }
            });

		backgroundLayout.setOnLongClickListener(new OnLongClickListener(){

				@Override
				public boolean onLongClick(View p1) {
					AlertDialog.Builder dialog =new AlertDialog.Builder(LauncherActivity.this);
                    dialog.setAdapter(new ArrayAdapter<String>(LauncherActivity.this, android.R.layout.simple_list_item_1, new String[]{"Change wallpaper"})
                        , new DialogInterface.OnClickListener(){

                            @Override
                            public void onClick(DialogInterface p1, int p2) {
                                switch (p2) {
                                    case 0:
                                        startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                                }
                            }
                        });
                    dialog.show();
					return true;
				}
			});

        appsGv.setOnItemClickListener(new OnItemClickListener(){

                @Override
                public void onItemClick(AdapterView<?> p1, View p2, int p3, long p4) {
                    final App app =(App) p1.getItemAtPosition(p3);
                    launchApp(null, app.getPackageName());
                }
            });

        appsGv.setOnItemLongClickListener(new OnItemLongClickListener(){

                @Override
                public boolean onItemLongClick(AdapterView<?> p1, View p2, int p3, long p4) {
                    showAppContextMenu(((App) p1.getItemAtPosition(p3)).getPackageName(), p2);
                    return true;
                }
            });

        registerReceiver(new BroadcastReceiver(){

                @Override
                public void onReceive(Context p1, Intent p2) {
                    String action=p2.getStringExtra("action");
                    if (action.equals("CONNECTED")) {
                        sendBroadcastToService(state);
                        serviceBtn.setVisibility(View.GONE);
                    } else if (action.equals("PINNED")) {
                        loadDesktopApps();
                    }
                }
            }, new IntentFilter(getPackageName() + ".SERVICE"){});
	}

    public void loadDesktopApps() {
        appsGv.setAdapter(new AppAdapterDesktop(this, AppUtils.getPinnedApps(this, getPackageManager(), AppUtils.DESKTOP_LIST)));
    }

	@Override
	protected void onResume() {
		super.onResume();
		state = "resume";
        sendBroadcastToService(state);

        if (DeviceUtils.isAccessibilityServiceEnabled(this))
            serviceBtn.setVisibility(View.GONE);
        else
            serviceBtn.setVisibility(View.VISIBLE);
        loadDesktopApps();
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("show_notes", false)) {
            notesEt.setVisibility(View.VISIBLE);
            loadNotes();

        } else {
            notesEt.setVisibility(View.GONE);
        }
        appsGv.requestFocus();
	}

	@Override
	protected void onPause() {
		super.onPause();
        state = "pause";
        sendBroadcastToService(state);
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("show_notes", false))
            saveNotes();

	}

    @Override
    public void onBackPressed() {
    }

    public void loadNotes() {
        File notes= new File(getExternalFilesDir(null), "notes.txt");
        try {
            BufferedReader br=new BufferedReader(new FileReader(notes));
            String line="";
            String noteContent="";
            while ((line = br.readLine()) != null) {
                noteContent += line + "\n";
            }
            br.close();
            notesEt.setText(noteContent);
        } catch (IOException e) {}


    }

    public void saveNotes() {
        String noteContent=notesEt.getText().toString();
        if (!noteContent.isEmpty()) {
            File notes= new File(getExternalFilesDir(null), "notes.txt");
            try {
                FileWriter fr = new FileWriter(notes);
                fr.write(noteContent);
                fr.close();
            } catch (IOException e) {}
        }
    }

    public void sendBroadcastToService(String action) {
        sendBroadcast(new Intent(getPackageName() + ".HOME").putExtra("action", action));
    }

    public void launchApp(String mode, String app) {
        sendBroadcast(new Intent(getPackageName() + ".HOME").putExtra("action", "launch").putExtra("mode", mode).putExtra("app", app));
    }

    private void showAppContextMenu(final String app, View p1) {
        PopupMenu pmenu=new PopupMenu(new ContextThemeWrapper(LauncherActivity.this, R.style.PopupMenuTheme), p1);

        Utils.setForceShowIcon(pmenu);

        final DeepShortcutManager shortcutManager = new DeepShortcutManager(this);

        if (shortcutManager.hasHostPermission()) {
            new DeepShortcutManager(p1.getContext()).addAppShortcutsToMenu(pmenu, app);
        }

        pmenu.inflate(R.menu.app_menu);

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("allow_app_freeze", false)) {
            MenuItem manageMenu = pmenu.getMenu().findItem(R.id.action_manage);
            manageMenu.getSubMenu().add(0, 8, 0, "Freeze").setIcon(R.drawable.ic_freeze);
        }
        pmenu.getMenu().add(0, 4, 0, "Remove").setIcon(R.drawable.ic_unpin);

        pmenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

                @Override
                public boolean onMenuItemClick(MenuItem p1) {
                    switch (p1.getItemId()) {
                        case R.id.action_appinfo:
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + app)));

                            break;
                        case R.id.action_uninstall:
                            startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + app)));
                            break;
                        case 4:
                            AppUtils.unpinApp(LauncherActivity.this, app, AppUtils.DESKTOP_LIST);
                            loadDesktopApps();
                            break;
                        case 7:
                            //do nothing
                            break;
                        case 8:
                            String status = DeviceUtils.runAsRoot("pm disable " + app);
                            if (!status.equals("error"))
                                Toast.makeText(LauncherActivity.this, "App frozen", Toast.LENGTH_SHORT).show();
                            else
                                Toast.makeText(LauncherActivity.this, "Something went wrong", Toast.LENGTH_SHORT).show();
                            break;
                        case R.id.action_launch_modes:
                            //do nothing
                            break;
                        case R.id.action_manage:
                            //do nothing
                            break;
                        case R.id.action_launch_standard:
                            launchApp("standard", app);
                            break;
                        case R.id.action_launch_maximized:
                            launchApp("maximized", app);
                            break;
                        case R.id.action_launch_portrait:
                            launchApp("portrait", app);
                            break;
                        case R.id.action_launch_fullscreen:
                            launchApp("fullscreen", app);
                            break;
                        default:
                            try {
                                ShortcutInfo shortcut = DeepShortcutManager.shortcutInfoMap.get(p1.getItemId());
                                if (shortcut != null) {
                                    shortcutManager.startShortcut(shortcut, shortcut.getId(), null);
                                }
                            } catch (Exception ignored) {
                                Toast.makeText(LauncherActivity.this, ignored.toString() + ignored.getMessage(), 5000).show();
                            }
                    }
                    return false;
                }
            });

        pmenu.show();

    }

    public class AppAdapterDesktop extends ArrayAdapter<App> {
        private Context context;
        private int iconBackground,iconPadding;
        public AppAdapterDesktop(Context context, ArrayList<App> apps) {
            super(context, R.layout.app_entry_desktop, apps);
            this.context = context;
            SharedPreferences sp=PreferenceManager.getDefaultSharedPreferences(context);
            iconPadding = Utils.dpToPx(context, Integer.parseInt(sp.getString("icon_padding", "4")) + 3);
            switch (sp.getString("icon_shape", "circle")) {
                case "circle":
                    iconBackground = R.drawable.circle;
                    break;
                case "round_rect":
                    iconBackground = R.drawable.round_square;
                    break;
                case "default":
                    iconBackground = -1;
                    break;
            }

        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView =   LayoutInflater.from(context).inflate(R.layout.app_entry_desktop, null);
            ImageView iconIv = convertView.findViewById(R.id.desktop_app_icon_iv);
            TextView nameTv=convertView.findViewById(R.id.desktop_app_name_tv);
            final App app = getItem(position);
            nameTv.setText(app.getName());
            if (iconBackground != -1) {
                iconIv.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
                iconIv.setBackgroundResource(iconBackground);
            }

            IconParserUtilities iconParserUtilities = new IconParserUtilities(context);

            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("icon_theming", false))
                iconIv.setImageDrawable(iconParserUtilities.getPackageThemedIcon(app.getPackageName()));
            else 
                iconIv.setImageDrawable(app.getIcon());

            convertView.setOnTouchListener(new OnTouchListener(){

                    @Override
                    public boolean onTouch(View p1, MotionEvent p2) {
                        if (p2.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                            showAppContextMenu(app.getPackageName(), p1);
                            return true;
                        }
                        return false;
                    }


                });


            return convertView;
        }
    }
}
