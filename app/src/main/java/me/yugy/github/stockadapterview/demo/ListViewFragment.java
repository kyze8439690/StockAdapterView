package me.yugy.github.stockadapterview.demo;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import me.yugy.github.stockadapterview.R;
import me.yugy.github.stockadapterview.library.AdapterView;
import me.yugy.github.stockadapterview.library.ListView;

public class ListViewFragment extends Fragment {
    
    private ListView mList;

    @Nullable 
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mList = new ListView(getActivity());
        return mList;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        int padding = getResources().getDimensionPixelSize(R.dimen.header_footer_padding);
        TextView header = new TextView(getActivity());
        header.setText("header");
        header.setTextColor(Color.WHITE);
        header.setBackgroundColor(Color.BLUE);
        header.setGravity(Gravity.CENTER);
        header.setPadding(padding, padding, padding, padding);
        mList.addHeaderView(header, null, false);

        TextView footer = new TextView(getActivity());
        footer.setText("footer");
        footer.setTextColor(Color.WHITE);
        footer.setBackgroundColor(Color.GREEN);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(padding, padding, padding, padding);
        mList.addFooterView(footer, null, false);

        mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String text = (String) parent.getAdapter().getItem(position);
                Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();
            }
        });

        mList.setAdapter(new BaseAdapter() {

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
