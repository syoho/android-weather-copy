package com.qingguweather.android.util;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.qingguweather.android.R;
import com.qingguweather.android.db.City;
import com.qingguweather.android.db.County;
import com.qingguweather.android.db.Province;
import com.qingguweather.android.util.HttpUtil;
import com.qingguweather.android.util.Utility;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Created by xiao on 2017/4/17.
 */

public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;
    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataList = new ArrayList<>();
    private List<Province> provinceList;
    private List<City> cityList;
    private List<County> countyList;
    private Province selectedProvince;
    private City selectedCity;
    private int currentLevel;


    /*获取控件实例，初始化ArrayAdapter*/
    public View onCreateView(LayoutInflater inflater , ViewGroup container, Bundle savedInstanceState){
        View view = inflater.inflate(R.layout.choose_area,container,false);
        titleText = (TextView) view.findViewById(R.id.title_text);
        backButton = (Button) view.findViewById(R.id.back_button);
        listView = (ListView) view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(),android.R.layout.simple_list_item_1,dataList); /*ListView适配器*/
        listView.setAdapter(adapter);
        return view;
    }


    /*给ListView和Button设置点击事件*/
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE) {
                    selectedProvince = provinceList.get(position);
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    selectedCity = cityList.get(position);
                    queryCounties();
                }
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLevel == LEVEL_COUNTY) {
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    queryProvinces();
                }
            }
        });
        queryProvinces();
    }

    /*查询县，优先查数据库，如没有再到服务器上查询*/

    private void queryProvinces() {
        backButton.setVisibility(View.GONE);/*将返回按钮隐藏*/
        titleText.setText("中国");  /*将布局标题设置为地区*/
        provinceList = DataSupport.findAll(Province.class);
        if (provinceList.size() > 0) {
            dataList.clear();
            for (Province province : provinceList) {
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        } else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address, "province");
        }
    }

    /*查询选中省得所有市，优先从数据库查，如没有再到服务器查*/
    private void queryCities(){
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
        cityList = DataSupport.where("provinceid = ?",String.valueOf(selectedProvince.getId())).find(City.class);
        if(cityList.size() > 0){
            dataList.clear();
            for(City city : cityList){
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        }else{
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(address ,"city" );
        }
    }

    /*查询选中市的县*/
    private void queryCounties() {
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList = DataSupport.where("cityid = ?", String.valueOf(selectedCity.getId())).find(County.class);
        if (countyList.size() > 0) {
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromServer(address, "county");
        }
    }

    /*根据地址和类型从服务器上查询数据*/
    private void queryFromServer(String address ,final String type) {
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            /*向服务器发送请求*/
            public void onResponse(Call call, Response response) throws IOException {   /*服务器响应数据回调到此处*/
                String responseText = response.body().string();
                boolean result = false;
                if ("province".equals(type)) {
                    result = Utility.handleProvinceResponse(responseText);   /*handleProvinceResponse（）解析处理服务器返回数据*/
                } else if ("city".equals(type)) {
                    result = Utility.handleCityResponse(responseText, selectedProvince.getId());
                } else if ("county".equals(type)) {
                    result = Utility.handleCountyResponse(responseText, selectedCity.getId());
                }
                if (result) {
                    getActivity().runOnUiThread(new Runnable() {     /*从子线程切换到主线程*/
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvinces();              /*重新加载省级数据*/
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
    /*显示进度对话框*/
    private void showProgressDialog(){
        if(progressDialog == null){
            progressDialog = new ProgressDialog(getActivity());
            progressDialog .setMessage("正在加载");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    /*关闭进度对话框*/
    private void closeProgressDialog(){
        if(progressDialog != null){
            progressDialog.dismiss();
        }
    }
}
