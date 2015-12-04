package me.yugy.github.stockadapterview.demo;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.view.View;

import me.yugy.github.stockadapterview.R;

public class MainActivity extends AppCompatActivity {

    private TabLayout mTabs;
    private ViewPager mPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTabs = (TabLayout) findViewById(R.id.tabs);
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                switch (position) {
                    case 0: return new ListViewFragment();
                    case 1: return new GridViewFragment();
                    case 2: return new NativeListViewFragment();
                    default: return null;
                }
            }

            @Override
            public int getCount() {
                return 3;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                switch (position) {
                    case 0: return "ListView";
                    case 1: return "GridView";
                    case 2: return "NativeListView";
                    default: return null;
                }
            }
        });
        mTabs.setupWithViewPager(mPager);
    }
}
