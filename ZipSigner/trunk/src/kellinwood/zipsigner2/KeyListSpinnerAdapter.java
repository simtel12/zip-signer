package kellinwood.zipsigner2;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import kellinwood.security.zipsigner.ZipSigner;
import kellinwood.zipsigner2.customkeys.Alias;
import kellinwood.zipsigner2.customkeys.CustomKeysDataSource;
import kellinwood.zipsigner2.customkeys.Keystore;

import java.util.ArrayList;
import java.util.List;

/**
 */
public class KeyListSpinnerAdapter extends ArrayAdapter<KeyEntry> {


    // Your sent context
    private Context context;
    // Your custom values for the spinner (User)
    private List<KeyEntry> values;

    public static KeyListSpinnerAdapter createInstance( Context context, int textViewResourceId) {
        return new KeyListSpinnerAdapter(context, textViewResourceId, buildKeyEntryList(context));
    }

    static List<KeyEntry> buildKeyEntryList( Context context) {
        List<KeyEntry> keyEntryList = new ArrayList<KeyEntry>();
        for (String mode : ZipSigner.SUPPORTED_KEY_MODES) {
            KeyEntry entry = new KeyEntry();
            entry.setId(-1);
            entry.setDisplayName(mode);
            entry.setHasPassword(false);
            keyEntryList.add( entry);
        }
        CustomKeysDataSource customKeysDataSource = new CustomKeysDataSource(context);
        customKeysDataSource.open();
        List<Keystore> keystoreList = customKeysDataSource.getAllKeystores();
        customKeysDataSource.close();

        for (Keystore keystore : keystoreList) {
            for (Alias alias : keystore.getAliases()) {
                if (alias.isSelected()) {
                    KeyEntry entry = new KeyEntry();
                    entry.setId( alias.getId());
                    entry.setDisplayName(alias.getDisplayName());
                    entry.setHasPassword( alias.getPassword() != null && alias.getPassword().length() > 0);
                    keyEntryList.add( entry);
                }
            }
        }
        return keyEntryList;
    }

    public KeyListSpinnerAdapter(Context context, int textViewResourceId, List<KeyEntry> values) {
        super(context, textViewResourceId, values);
        this.context = context;
        this.values = values;
    }

    public int getCount() {
        return values.size();
    }

    public KeyEntry getItem(int position) {
        return values.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public void changeData() {
        values = buildKeyEntryList(context);
        super.notifyDataSetChanged();
    }

    // And the "magic" goes here
    // This is for the "passive" state of the spinner
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // I created a dynamic TextView here, but you can reference your own  custom layout for each spinner item
        TextView label = new TextView(context);
        label.setTextColor(Color.BLACK);
        // Then you can get the current item using the values array (Users array) and the current position
        // You can NOW reference each method you has created in your bean object (User class)
        label.setText(values.get(position).getDisplayName());

        // And finally return your dynamic (or custom) view for each spinner item
        return label;
    }

    // And here is when the "chooser" is popped up
    // Normally is the same view, but you can customize it if you want
    @Override
    public View getDropDownView(int position, View convertView,
                                ViewGroup parent) {
        TextView label = new TextView(context);
        label.setTextColor(Color.BLACK);
        label.setText(values.get(position).getDisplayName());

        return label;
    }
}
