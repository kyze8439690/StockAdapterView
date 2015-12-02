package me.yugy.github.stockadapterview.demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import me.yugy.github.stockadapterview.R;
import me.yugy.github.stockadapterview.library.GridView;

public class GridViewFragment extends Fragment {

    private GridView mGrid;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mGrid = new GridView(getActivity());
        return mGrid;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mGrid.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return 20;
            }

            @Override
            public Object getItem(int position) {
                return null;
            }

            @Override
            public long getItemId(int position) {
                return 0;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) convertView;
                if (item == null) {
                    item = new TextView(parent.getContext());
                    item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                    item.setGravity(Gravity.CENTER);
                    int padding = getResources().getDimensionPixelSize(R.dimen.grid_item_padding);
                    item.setPadding(padding, padding, padding, padding);
                }
                item.setText(String.valueOf(position));
                return item;
            }
        });
    }
}
