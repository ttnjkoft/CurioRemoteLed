package com.example.zhy_horizontalscrollview;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.zhy_horizontalscrollview.MyHorizontalScrollView.CurrentImageChangeListener;
import com.example.zhy_horizontalscrollview.MyHorizontalScrollView.OnItemClickListener;

public class MainActivity extends Activity
{
	private final static String TAG =MainActivity.class.getName();
	private MyHorizontalScrollView mHorizontalScrollView;
	private HorizontalScrollViewAdapter mAdapter;
	private boolean mScanning;
	private static final long SCAN_PERIOD = 10000;
	private static final int REQUEST_ENABLE_BT = 1;
	private ImageView mImg;
	private int rssiVal;
	private int curioIndex;
	private boolean mConnected = false;
	private boolean ledStatus=false;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothLeService mBluetoothLeService;
	private BluetoothGattCharacteristic characteristic;
	private CircularSeekBar myseekbar;
	private AlertDialog.Builder builder;
	private long startTime;
	private int[] imagesnot={R.mipmap.hypetablenot,R.mipmap.hypeclampnot,R.mipmap.hypefloornot,R.mipmap.hypesuspendednot,
			R.mipmap.structotablenot,R.mipmap.structofloornot,R.mipmap.superlighttablenot,R.mipmap.structotablenot};
	public static final int[] images={R.mipmap.hypetable,R.mipmap.hypeclamp,R.mipmap.hypefloor,R.mipmap.hypesuspended,
			R.mipmap.structotable,R.mipmap.structofloor,R.mipmap.superlighttable,R.mipmap.structotable};
	private String[] devName={"Hype Table","Hype Clamp","Hype Floor","Hype Suspended",
			"Structo Table","Structo Floor","Superlight Table","Structo Table"};
	private ArrayList<Item> dataItem=new ArrayList<Item>();
	private LinkedList<Item> listDataItem=new LinkedList<Item>();
	private LinkedList<BluetoothDevice> mDeviceContainer = new LinkedList<BluetoothDevice>();
	private ArrayList<BluetoothDevice> mLeDevices=new ArrayList<BluetoothDevice>();

	private Handler mHandler ;
	private SensorManager sensorManager;
	private Vibrator vibrator;
	private int index;
	private static final int SENSOR_SHAKE = 10;
	private static final int SENSOR_TIME_MIN_GAP = 1500;//ms

	private static final int curiomaf=0x7dcc;
	public static  int txPowerLevel = Integer.MIN_VALUE;
	private static final int DATA_TYPE_FLAGS = 0x01;
	private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL = 0x02;
	private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE = 0x03;
	private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL = 0x04;
	private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE = 0x05;
	private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL = 0x06;
	private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 0x07;
	private static final int DATA_TYPE_LOCAL_NAME_SHORT = 0x08;
	private static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 0x09;
	private static final int DATA_TYPE_TX_POWER_LEVEL = 0x0A;
	private static final int DATA_TYPE_SERVICE_DATA = 0x16;
	private static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF;
	private static final int[] imagesvaules={1,2,3,4,5,6,8,10};
	private BluetoothDevice mDevice;
	private ItemDAO mSQL;
	private String mDeviceName;
	private String mDeviceAddress;
	private	int mDeviceShake;
	private	int mDeviceImage;
	private int mDeviceImageNot;
	private Boolean firstcall=true;
	private TextView selectDeviceName;
	private TextView seekbarValue;

	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			Log.e(TAG, "mBluetoothLeService is okay");

		}

		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		findViews();
		checkBle();
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
	}

	private void checkBle()
	{
		// 檢查手機硬體是否為BLE裝置
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
			finish();
		}
		//初始藍牙Adapter
		final BluetoothManager bluetoothManager =
				(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
		// 檢查手機使否開啟藍芽裝置
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		new Thread(new Runnable() {
			public void run() {
				if (mBluetoothAdapter.isEnabled()){
					scanLeDevice(true);
					mScanning = true;
				}else {
					Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
					startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
				}
			}
		}).start();
	}

	private void findViews()
	{
		mHandler=new Handler();
		mImg = (ImageView) findViewById(R.id.id_content);
		mHorizontalScrollView = (MyHorizontalScrollView) findViewById(R.id.id_horizontalScrollView);
		mAdapter = new HorizontalScrollViewAdapter(this,dataItem);
//		mAdapter = new HorizontalScrollViewAdapter(this,conDataItem);
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		startTime=System.currentTimeMillis();
		vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		mSQL=new ItemDAO(this);
		myseekbar=(CircularSeekBar)findViewById(R.id.view);
		selectDeviceName=(TextView)findViewById(R.id.textView);
		seekbarValue=(TextView)findViewById(R.id.textView2);
		mImg.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				Intent intent = new Intent(v.getContext(), SettngDeviceActivity.class);
				intent.putExtra(SettngDeviceActivity.EXTRAS_DEVICE_ADDRESS, dataItem.get(index).getDevice().getAddress());
				intent.putExtra(SettngDeviceActivity.EXTRAS_DEVICE_NAME, dataItem.get(index).getDevicename());
				intent.putExtra(SettngDeviceActivity.EXTRAS_DEVICE_IMAGE, dataItem.get(index).getImage());
				intent.putExtra(SettngDeviceActivity.EXTRAS_SHAKE_FLAG, dataItem.get(index).getShake());
				startActivityForResult(intent, 77);
				return true;
			}
		});



		//添加滚动回调
		mHorizontalScrollView
				.setCurrentImageChangeListener(new CurrentImageChangeListener() {
					@Override
					public void onCurrentImgChanged(int position,
													View viewIndicator) {

					}
				});


		//添加点击回调
		mHorizontalScrollView.setOnItemClickListener(new OnItemClickListener()
		{

			@Override
			public void onClick(View view, int position)
			{

				Item item=dataItem.get(position);



				characteristic=mBluetoothLeService.getCurioCharacteristic(item.getDevice());
					if(characteristic!=null)
					{
						item.setmGattCharacteristics(characteristic);
						item.setDeviceImage(item.getImagenot());
						index=position;
						mDevice=item.getDevice();
						mDeviceShake=item.getShake();
						mImg.setImageResource(item.getImage());
						selectDeviceName.setText(item.getDevicename());
						ledStatus=item.getLedStatus();

						drewScrollView();
					}
					else
					{
						mImg.setImageResource(0);
						selectDeviceName.setText("");
						mDevice=null;
						Log.d(TAG, "gat is Null");

					}
//				}

			}
		});

		myseekbar.setSeekBarChangeListener(new CircularSeekBar.OnSeekChangeListener() {
			@Override
			public void onProgressChange(CircularSeekBar view, int newProgress) {

//				if (newProgress <= 10) newProgress = 10;
				seekbarValue.setText(newProgress+"%");
				byte[] value = new byte[10];
				value[0] = (byte) 0x00;
				StringBuilder sb = new StringBuilder();
				sb.append(Integer.toHexString((int)Math.floor(newProgress*1.27)));
				if (sb.length() < 2) {
					sb.insert(0, '0'); // pad with leading zero if needed
				}
//				if (characteristic != null) {
					String hex = sb.toString();
//					characteristic.setValue(hex2byte(hex.getBytes()));
//					mBluetoothLeService.writeCharacteristic(characteristic);

				mBluetoothLeService.writeCharacteristic(characteristic,hex2byte(hex.getBytes()));
					ledStatus = true;

//				}
//				else{dialog();}

			}
		});
//		myseekbar.setOnSeekBarChangeListener(new curioSeekBar.OnSeekBarChangeListener() {
//			@Override
//			public void onProgressChanged(curioSeekBar VerticalSeekBar, int progress, boolean fromUser) {
//				if (progress <= 10) progress = 10;
//				byte[] value = new byte[20];
//				value[0] = (byte) 0x00;
//				StringBuilder sb = new StringBuilder();
//				sb.append(Integer.toHexString(progress));
//				if (sb.length() < 2) {
//					sb.insert(0, '0'); // pad with leading zero if needed
//				}
//				if (characteristic != null) {
//					String hex = sb.toString();
//					characteristic.setValue(value[0], BluetoothGattCharacteristic.FORMAT_UINT8, 0);
//					characteristic.setValue(hex2byte(hex.getBytes()));
//					mBluetoothLeService.writeCharacteristic(characteristic);
//					ledStatus = true;
//				}
//			}
//
//			@Override
//			public void onStartTrackingTouch(curioSeekBar VerticalSeekBar) {
//
//			}
//
//			@Override
//			public void onStopTrackingTouch(curioSeekBar VerticalSeekBar) {
//
//			}
//		});
		if (sensorManager != null) {// 注册监听器
			sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
			// 第一个参数是Listener，第二个参数是所得传感器类型，第三个参数值获取传感器信息的频率
		}
	}

	@Override
	protected void onStart() {
		super.onStart();

	}

	protected void dialog() {
		if (builder == null) {
			builder = new AlertDialog.Builder(this);
			builder.setMessage("請選擇一個裝置");
			builder.setTitle("錯誤!!");
			builder.setPositiveButton("確定", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					builder = null;
				}
			});
			builder.show();
		}
	}


	@Override
	protected void onResume() {
		super.onResume();
//		if (!mBluetoothAdapter.isEnabled()) {
//			if (!mBluetoothAdapter.isEnabled()) {
//				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//			}
//		}
//
////		dataItem.clear();
////		mLeDevices.clear();
//		scanLeDevice(true);





	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 77){
			switch (resultCode) {
				case Activity.RESULT_OK:
					Item item = dataItem.get(index);
					mDeviceAddress = data.getStringExtra(SettngDeviceActivity.EXTRAS_DEVICE_ADDRESS);
					mDeviceName = data.getStringExtra(SettngDeviceActivity.EXTRAS_DEVICE_NAME);
					mDeviceShake = data.getIntExtra(SettngDeviceActivity.EXTRAS_SHAKE_FLAG, 0);
					item.setDevicename(mDeviceName);
					item.setShake(mDeviceShake);
					item.setDeviceImage(item.getImagenot());
					item.setLedStatus(false);
					selectDeviceName.setText(mDeviceName);
					dataItem.set(index, item);
					mSQL.update(mDeviceAddress, mDeviceShake, mDeviceName);
//					Log.e(TAG, "ActivityResult name=" + mDeviceName + "index=" + index);
					mHorizontalScrollView.initFirstScreenChildren(dataItem.size());

					break;
				default:
					break;
			}
		}
		else if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
			finish();
			return;
		}
//		else if(requestCode == Activity.RESULT_OK)
//		{
			scanLeDevice(true);
			mScanning = true;
			return;
//		}


//		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onPause() {
		super.onPause();
		scanLeDevice(false);

		if (sensorManager != null) {// 取消监听器
			sensorManager.unregisterListener(sensorEventListener);}

	}
	private SensorEventListener sensorEventListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			// 传感器信息改变时执行该方法
			float[] values = event.values;
			float x = values[0]; // x轴方向的重力加速度，向右为正
			float y = values[1]; // y轴方向的重力加速度，向前为正
			float z = values[2]; // z轴方向的重力加速度，向上为正
//			Log.i(TAG, "x轴方向的重力加速度" + x + "；y轴方向的重力加速度" + y + "；z轴方向的重力加速度" + z);
			// 一般在这三个方向的重力加速度达到40就达到了摇晃手机的状态。
			int medumValue = 19;// 三星 i9250怎么晃都不会超过20，没办法，只设置19了
			if(mDeviceShake==1){
				if (Math.abs(x) > medumValue || Math.abs(y) > medumValue || Math.abs(z) > medumValue) {

					if(characteristic!=null)
					{
						long endTime=System.currentTimeMillis();
							if((endTime-startTime)>=SENSOR_TIME_MIN_GAP)
							{
								vibrator.vibrate(100);
								if (ledStatus)turnled(true);
								else turnled(false);
								startTime=endTime;
								dataItem.get(index).setLedStatus(ledStatus);

							}

					}
				}
			}
		}
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {

		}

	};

	@Override
	protected void onStop() {
		super.onStop();


	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mGattUpdateReceiver);
		unbindService(mServiceConnection);

		if(mBluetoothLeService != null)
		{
			mBluetoothLeService.close();
			mBluetoothLeService = null;
		}

		Log.i(TAG, "MainActivity closed!!!");
	}






	private void scanLeDevice(final boolean enable) {

		if (enable) {
//			dataItem.clear();
//			mLeDevices.clear();
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					mScanning = false;
					mBluetoothAdapter.stopLeScan(mLeScanCallback);

				}
			}, SCAN_PERIOD);

			mScanning = true;
			mBluetoothAdapter.startLeScan(mLeScanCallback);
		} else {
			mScanning = false;
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
		}

	}
	private BluetoothAdapter.LeScanCallback mLeScanCallback =
			new BluetoothAdapter.LeScanCallback() {

				@Override
				public void onLeScan(final BluetoothDevice device, int rssi, final byte[] scanRecord) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							//如果找到的是curio的裝置就連接裝置,並取得控制光源的服務
//
							if (getmufid(device, curiomaf, scanRecord) != null) {
								if (!mDeviceContainer.isEmpty()) {
									if(!isEquals(device)){
										connectBle(device);
									}
								}else{
									connectBle(device);
								}
//									mBluetoothLeService.connect(device.getAddress());
//								characteristic=getCuriocharacteristic(mBluetoothLeService.getGatt(device));


							}

						}
					});
				}
			};
	private boolean isEquals(BluetoothDevice device){
		for(Item item:dataItem){
			if(item.getDevice().equals(device)){return true;}
		}

//		for(BluetoothDevice mDdevice: mDeviceContainer){
//			if(mDdevice.equals(device)){
//				return true;
//			}
//		}
		return false;
	}
	private void connectBle(BluetoothDevice device) {
		mDeviceContainer.add(device);
		while (true) {
			if (mBluetoothLeService != null) {
				mBluetoothLeService.connect(device.getAddress());
				break;
			} else {
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	private  byte[] getmufid(final BluetoothDevice device,int manufacturerid,byte[] scanRecord){
		if (scanRecord == null) {
			return null;
		}
		int currentPos = 0;
		int advertiseFlag = -1;
		String localName = null;


		SparseArray<byte[]> manufacturerData = new SparseArray<byte[]>();

		try {
			while (currentPos < scanRecord.length) {
				// length is unsigned int.
				int length = scanRecord[currentPos++] & 0xFF;
				if (length == 0) {
					break;
				}
				// Note the length includes the length of the field type itself.
				int dataLength = length - 1;
				// fieldType is unsigned int.
				int fieldType = scanRecord[currentPos++] & 0xFF;
				switch (fieldType) {
					case DATA_TYPE_FLAGS:
						advertiseFlag = scanRecord[currentPos] & 0xFF;
						break;
					case DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL:
					case DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE:
						break;
					case DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL:
					case DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE:
						break;
					case DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL:
					case DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE:
						break;
					case DATA_TYPE_LOCAL_NAME_SHORT:
					case DATA_TYPE_LOCAL_NAME_COMPLETE:
						localName = new String(
								extractBytes(scanRecord, currentPos, dataLength));
						break;
					case DATA_TYPE_TX_POWER_LEVEL:
						txPowerLevel = scanRecord[currentPos];
						break;
					case DATA_TYPE_SERVICE_DATA:
						break;
					case DATA_TYPE_MANUFACTURER_SPECIFIC_DATA:
						//curio的manufacturerID是0x7dcc
						//byte格式:FF,CC,7D,目前pwm值(low),目前pwm值(hight),BoardType

						int manufacturerId = ((scanRecord[currentPos + 1] & 0xFF) << 8)
								+ (scanRecord[currentPos] & 0xFF);

						byte[] manufacturerDataBytes = extractBytes(scanRecord, currentPos + 2,
								dataLength - 2);
						if(manufacturerId==manufacturerid)
						{

							if(!mLeDevices.contains(device))
							{
								//尋找查到的image值位於圖的第幾個index
								curioIndex=find(imagesvaules,scanRecord[currentPos + 4] & 0xFF);
							 if (curioIndex >= 0 & curioIndex <= images.length)
								{
										mDeviceName=devName[curioIndex];
										mDeviceImage=images[curioIndex];
										mDeviceImageNot=imagesnot[curioIndex];
								}
								else
								{
									mDeviceName="UNKNOW Curio Device";
									mDeviceImage=R.mipmap.curioicon;
									mDeviceImageNot=R.mipmap.curioicon;
								}
								mSQL.insert(device.getAddress(), 0, mDeviceName);
								Item mdata=mSQL.select(device.getAddress()).get(0);
								mdata.setDevice(device);
								mdata.setMac(device.getAddress());
								mdata.setImage(mDeviceImage);
								mdata.setImagenot(mDeviceImageNot);
								mdata.setDeviceImage(mDeviceImage);
								mdata.setLedStatus(false);
//								mLeDevices.add(device);

								listDataItem.add(mdata);


							}
							manufacturerData.put(manufacturerId, manufacturerDataBytes);

						}
						break;

					default:

						break;
				}
				currentPos += dataLength;
			}
			return manufacturerData.get(manufacturerid);
		} catch (Exception e) {

			return null;
		}

	}
	private void drewScrollView(){
		if(!dataItem.isEmpty()) {
			if (firstcall) {
				mHorizontalScrollView.initDatas(mAdapter);
				firstcall = false;
			} else mHorizontalScrollView.initFirstScreenChildren(dataItem.size());
		}
	}

	private static byte[] extractBytes(byte[] scanRecord, int start, int length) {
		byte[] bytes = new byte[length];
		System.arraycopy(scanRecord, start, bytes, 0, length);
		return bytes;
	}

	public int find(int[] array, int value)  //找指定值位於陣列的那個位置
	{
		int temp=-1;
		for(int i=0; i<array.length; i++) {
			if (array[i] == value)
				temp = i;
		}
		return temp;

	}
		//廣播
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				mConnected = true;


			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				if (!dataItem.isEmpty()) {
					String strAddress = intent.getStringExtra("DEVICE_ADDRESS");
					if(removeDevice(strAddress)){
						drewScrollView();
					}
				}
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				if (!mDeviceContainer.isEmpty()) {
					String strAddress = intent.getStringExtra("DEVICE_ADDRESS");
					for(BluetoothDevice bluetoothDevice: mDeviceContainer){
						if(bluetoothDevice.getAddress().equals(strAddress)){
							if(!mLeDevices.equals(bluetoothDevice)) {
								mLeDevices.add(bluetoothDevice);
								for (Item item : listDataItem) {
									if (item.getDevice().equals(bluetoothDevice)) {
										if (!dataItem.equals(item)) {
											dataItem.add(item);

										}
									}
								}
							}
						}
					}
					drewScrollView();
				}


			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

			}
		}
	};
	private byte[] hex2byte(byte[] b) {
		if ((b.length % 2) != 0) {
			throw new IllegalArgumentException("長度不是偶數");
		}
		byte[] b2 = new byte[b.length / 2];
		for (int n = 0; n < b.length; n += 2) {
			String item = new String(b, n, 2);
			// 两位一组，表示一个字节,把这样表示的16进制字符串，还原成一个进制字节
			b2[n / 2] = (byte) Integer.parseInt(item, 16);
		}
		b = null;
		return b2;
	}
	private void clearDevice() {
		mBluetoothLeService.close();
		mLeDevices.clear();
		dataItem.clear();
	}
	private boolean removeDevice(String strAddress) {
		for(final Item item:dataItem){
			if(item.getDevice().getAddress().equals(strAddress)){
				new Thread(new Runnable() {
					public void run() {
						try {
							Thread.sleep(250);
							dataItem.remove(item);
							mLeDevices.remove(item.getDevice());
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}).start();
				return true;
			}
		}
		return false;
	}

	private void turnled(boolean onoff){
		byte[] value = new byte[2];
		value[0] = (byte) 0x00;
		value[1] =(byte) 0x5a;

		if (characteristic != null) {
			if(onoff) {
//				characteristic.setValue(value[0], BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				myseekbar.setProgress(0);
				ledStatus=false;
			}
			else

			{
//				characteristic.setValue(value[1], BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				myseekbar.setProgress(100);
				
				ledStatus=true;
			}

//			mBluetoothLeService.writeCharacteristic(characteristic);

		}

	}

//	private BluetoothGattCharacteristic getCuriocharacteristic(BluetoothGatt gat){
//		BluetoothGattService mgat=mBluetoothLeService.getGattserver(gat,
//				BluetoothLeService.UUID_CRIO_LIGHT_DEVICE);
//		if (mgat!=null)	{return mgat.getCharacteristic(BluetoothLeService.UUID_PWM_brightness_level);}
//		else{return null;}
//
//	}
	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);

		return intentFilter;
	}





}
