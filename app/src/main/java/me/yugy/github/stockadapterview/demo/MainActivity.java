package me.yugy.github.stockadapterview.demo;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

import me.yugy.github.stockadapterview.R;
import me.yugy.github.stockadapterview.library.ListView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ListView list = (ListView) findViewById(R.id.list);
        int padding = getResources().getDimensionPixelSize(R.dimen.header_footer_padding);
        TextView header = new TextView(this);
        header.setText("header");
        header.setTextColor(Color.WHITE);
        header.setBackgroundColor(Color.RED);
        header.setGravity(Gravity.CENTER);
        header.setPadding(padding, padding, padding, padding);
        list.addHeaderView(header, null, false);

        TextView footer = new TextView(this);
        footer.setText("footer");
        footer.setTextColor(Color.WHITE);
        footer.setBackgroundColor(Color.RED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(padding, padding, padding, padding);
        list.addFooterView(footer, null, false);

        list.setAdapter(new BaseAdapter() {

            private final String[] DATA = new String[]{
                    "Adapter implementation",
                    "Simple List View In Android",
                    "Create List View Android",
                    "Android Example",
                    "List View Source Code",
                    "List View Array Adapter",
                    "Android Example List View",
                    "Adapter implementation",
                    "Simple List View In Android",
                    "Create List View Android",
                    "Android Example",
                    "List View Source Code",
                    "List View Array Adapter",
                    "Android Example List View",
                    "Adapter implementation",
                    "Simple List View In Android",
                    "Create List View Android",
                    "Android Example",
                    "List View Source Code",
                    "List View Array Adapter",
                    "Android Example List View",
                    "Adapter implementation",
                    "Simple List View In Android",
                    "Create List View Android",
                    "Android Example",
                    "List View Source Code",
                    "List View Array Adapter",
                    "Android Example List View"
            };

            @Override
            public int getCount() {
                return DATA.length;
            }

            @Override
            public String getItem(int position) {
                return DATA[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) convertView;
                if (item == null) {
                    item = (TextView) LayoutInflater.from(parent.getContext()).inflate(
                            android.R.layout.simple_list_item_1, parent, false);
                }
                item.setText(getItem(position));
                return item;
            }
        });
    }
}
