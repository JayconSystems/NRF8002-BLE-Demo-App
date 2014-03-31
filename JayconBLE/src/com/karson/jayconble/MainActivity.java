package com.karson.jayconble;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity implements
		BluetoothAdapter.LeScanCallback, BluetoothProfile {

	private final static int REQUEST_ENABLE_BT = 1;

	private static final UUID GENERIC_ACCESS_SERVICE = UUID
			.fromString("00001800-0000-1000-8000-00805f9b34fb");
	private static final UUID DEVICE_NAME_CHAR = UUID
			.fromString("00002A00-0000-1000-8000-00805f9b34fb");

	private static final UUID BATTERY_SERVICE = UUID
			.fromString("0000180F-0000-1000-8000-00805f9b34fb");
	private static final UUID BATTERY_LEVEL_CHAR = UUID
			.fromString("00002A19-0000-1000-8000-00805f9b34fb");

	private static final UUID IMMEDIATE_ALERT_SERVICE = UUID
			.fromString("00001802-0000-1000-8000-00805f9b34fb");
	private static final UUID ALERT_LEVEL_CHAR = UUID
			.fromString("00002A06-0000-1000-8000-00805f9b34fb");

	private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID
			.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private static final int ALERT_LEVEL_CHARACTERISTIC_VALUE = 2;
	private static final int ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE = 17;
	private static final int ALERT_LEVEL_CHARACTERISTIC_OFFSET = 0;

	private static final byte[] ALERT_LEVEL_MILD = new byte[] {0x01};
	
	private BluetoothAdapter mBluetoothAdapter;
	private SparseArray<BluetoothDevice> mDevices;

	private BluetoothGatt mConnectedGatt;
	BluetoothDevice device;

	ArrayList<String> listItems = new ArrayList<String>();
	ArrayAdapter<String> myarrayAdapter;

	ListView lv;
	BluetoothGatt gattOutside;

	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Bundle bundle = msg.getData();
			String string = bundle.getString("myKey");
			TextView myTextView = (TextView) findViewById(R.id.textView);
			myTextView.setText(string);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_main);
		setProgressBarIndeterminate(true);

		lv = (ListView) findViewById(R.id.listView);
		myarrayAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, listItems);
		lv.setAdapter(myarrayAdapter);
		lv.setTextFilterEnabled(true);

		Log.e("BLEMainActivity:onCreate", "Entering OnCreate Activity");

		// Initializes Bluetooth adapter.
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
		mDevices = new SparseArray<BluetoothDevice>();

		// Ensures Bluetooth is available on the device and it is enabled. If
		// not,
		// displays a dialog requesting user permission to enable Bluetooth.

		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				String itemName = ((TextView) arg1).getText().toString();
				updateText("Connecting to " + itemName);
				Log.i("BLEMainActivity:lv.setOnItemClickListener",
						"Connecting to " + itemName);
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				connectToDevice(arg2);
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	// Start function when Scan button is clicked
	public void startBleScan(View view) {
		Log.i("BLEMainActivity:StartBLEScan", "Starting BLE Scan");
		Runnable runnable = new Runnable() {
			public void run() {
				handler.post(mStartRunnable);
			}
		};

		Thread mythread = new Thread(runnable);
		mythread.start();
	}

	private Runnable mStopRunnable = new Runnable() {
		@Override
		public void run() {
			stopScan();
			updateText("Press Button To Scan");
			handler.post(mListPopulate);
		}
	};

	private Runnable mStartRunnable = new Runnable() {
		@Override
		public void run() {
			updateText("Scan Started");
			mDevices.clear();
			startScan();
		}
	};

	private void updateText(String msgToSend) {
		Message msg = handler.obtainMessage();
		Bundle bundle = new Bundle();
		bundle.putString("myKey", msgToSend);
		msg.setData(bundle);
		handler.sendMessage(msg);
	}

	private Runnable mListPopulate = new Runnable() {
		@Override
		public void run() {
			listItems.clear();
			for (int i = 0; i < mDevices.size(); i++) {
				device = mDevices.valueAt(i);
				listItems.add(device.getName());
			}
			myarrayAdapter.notifyDataSetChanged();
		}
	};

	private void startScan() {
		mBluetoothAdapter.startLeScan(this);
		setProgressBarIndeterminateVisibility(true);

		handler.postDelayed(mStopRunnable, 2500);
	}

	private void stopScan() {
		mBluetoothAdapter.stopLeScan(this);
		setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
		mDevices.put(device.hashCode(), device);
		Log.i("BLEMainActivity.onLeScan",
				"List of devices = " + mDevices.toString());
	}

	// ====================== Connecting and communicating
	// =============================

	public void connectToDevice(int i) {
		BluetoothDevice device = mDevices.valueAt(i);
		mConnectedGatt = device.connectGatt(this, true, mGattCallback);
		Log.i("BLEMainActivity:ConnectToDevice",
				"Device State " + device.getBondState());

	}

	private void getName(BluetoothGatt gatt) {
		BluetoothGattCharacteristic characteristic;

		characteristic = gatt.getService(GENERIC_ACCESS_SERVICE)
				.getCharacteristic(DEVICE_NAME_CHAR);
		gatt.readCharacteristic(characteristic);
		Log.i("BLEMainActivity:GetName",
				"Name of device = " + gatt.getConnectionState(device));

	}

	public void getBattery(View view) {
		BluetoothGattCharacteristic characteristic = null;

		characteristic = gattOutside.getService(BATTERY_SERVICE)
				.getCharacteristic(BATTERY_LEVEL_CHAR);
		Log.i("BLEMainActivity:GetBattery", "Characteristic = "
				+ characteristic.toString());

		Boolean b = gattOutside.readCharacteristic(characteristic);
		Log.i("BLEMainActivity:GetBattery", "Reads the battery information "
				+ String.valueOf(b));
	}

	public void soundAlarm(View view) {
		BluetoothGattCharacteristic characteristic;
		characteristic = gattOutside.getService(IMMEDIATE_ALERT_SERVICE)
				.getCharacteristic(ALERT_LEVEL_CHAR);
		characteristic.setValue(ALERT_LEVEL_MILD);

		gattOutside.writeCharacteristic(characteristic);
	}

	private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicChanged(gatt, characteristic);
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			super.onCharacteristicRead(gatt, characteristic, status);
			if (DEVICE_NAME_CHAR.equals(characteristic.getUuid())) {
				String DevName = characteristic.getStringValue(0);
				Log.i("BLEMainActivity:onCharacteristicRead", "Device Name is "
						+ DevName + " Status = " + status);
				updateText("Device Name is " + DevName);
			}

			if (BATTERY_LEVEL_CHAR.equals(characteristic.getUuid())) {
				int BattLevel = characteristic.getIntValue(
						BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				Log.i("BLEMainActivity:onCharacteristicRead",
						"Battery Level is " + BattLevel);
				updateText("Battery Level = " + BattLevel);
			}

			
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {

			super.onCharacteristicWrite(gatt, characteristic, status);
			Log.i("BLEMainActivity:onCharacteristicWrite", "Status = " + status);
			updateText("Alarm Sounded!");

		}

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {
			Log.i("BLEMainActivity:onConnectionStateChanged",
					"Current status is " + status);

			if (status == BluetoothGatt.GATT_SUCCESS
					&& newState == BluetoothProfile.STATE_CONNECTED) {
				/*
				 * Once successfully connected, we must next discover all the
				 * services on the device before we can read and write their
				 * characteristics.
				 */
				gattOutside = gatt;
				gatt.discoverServices();
				if (gatt.discoverServices()) {
					updateText("Discovering Services");
					Log.e("BLEMainActivity:onConnectionStateChange",
							"Discovering Services");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					updateText("Failed to Discover Services");
					Log.e("BLEMainActivity:ConnectionStateChange",
							"Failed to discover Services");

				}
			} else if (status == BluetoothGatt.GATT_SUCCESS
					&& newState == BluetoothProfile.STATE_DISCONNECTED) {
				/*
				 * If at any point we disconnect, send a message to clear the
				 * weather values out of the UI
				 */
				Log.e("BLEMainActivity.ConnectionStateChange",
						"Disconnected State");
				updateText("Disconnected on 1");
			} else if (status != BluetoothGatt.GATT_SUCCESS) {
				/*
				 * If there is a failure at any stage, simply disconnect
				 */
				Log.e("BLEMainActivity:ConnectionStateChange",
						"Bluetooth Failure");
				gatt.disconnect();
				updateText("Disconnected on 2");
			}
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			super.onDescriptorRead(gatt, descriptor, status);
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			super.onDescriptorWrite(gatt, descriptor, status);
		}

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			super.onReadRemoteRssi(gatt, rssi, status);
		}

		@Override
		public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
			super.onReliableWriteCompleted(gatt, status);
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			super.onServicesDiscovered(gatt, status);
			Log.i("BLEMainActivity:onServicesDiscoverd",
					"Service Discover completed. Status = " + status);
			updateText("Connected");
			getName(gatt);

		}

	};

	/**
	 * Enables or disables notification on a give characteristic.
	 * 
	 * @param characteristic
	 *            Characteristic to act on.
	 * @param enabled
	 *            If true, enable notification. False otherwise.
	 */
	public void setCharacteristicNotification(
			BluetoothGattCharacteristic characteristic, boolean enabled) {
		if (mBluetoothAdapter == null || mConnectedGatt == null) {
			Log.i("BLEMainActivity:setCharacteristicNotification",
					"BluetoothAdapter not initialized");
			return;
		}
		mConnectedGatt.setCharacteristicNotification(characteristic, enabled);

		// This is specific to Immediate Alert Profile
		if (IMMEDIATE_ALERT_SERVICE.equals(characteristic.getUuid())) {
			BluetoothGattDescriptor descriptor = characteristic
					.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
			descriptor
					.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			mConnectedGatt.writeDescriptor(descriptor);
		}

	}

	@Override
	public List<BluetoothDevice> getConnectedDevices() {
		return null;
	}

	@Override
	public int getConnectionState(BluetoothDevice arg0) {
		return 0;
	}

	@Override
	public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] arg0) {
		return null;
	}

	/**
	 * Setup the Android device as a Gatt server and the nRF8002 as a peripheral
	 * 
	 */

	public void setupGattServer(View view) {

		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothGattServerCallback mBluetoothGattServerCallback = new BluetoothGattServerCallback() {
			@Override
			public void onConnectionStateChange(BluetoothDevice device,
					int status, int newState) {
				Log.i("BLEMainActivity:BluetoothGattServerCallback:onConnectionStateChange",
						"device : " + device + " status : " + status
								+ " new state : " + newState);
			}

			@Override
			public void onServiceAdded(int status, BluetoothGattService service) {
				Log.i("BLEMainActivity:BluetoothGattServerCallback:onServiceAdded",
						"service : " + service.getUuid() + " status = "
								+ status);
			}

			@Override
			public void onCharacteristicReadRequest(BluetoothDevice device,
					int requestId, int offset,
					BluetoothGattCharacteristic characteristic) {
				Log.i("BLEMainActivity:BluetoothGattServerCallback:onCharacteristicReadRequest",
						"device : " + device.getAddress() + " request = "
								+ requestId + " offset = " + offset
								+ " characteristic = "
								+ characteristic.getUuid());
		
			}

			@Override
			public void onCharacteristicWriteRequest(BluetoothDevice device,
					int requestId, BluetoothGattCharacteristic characteristic,
					boolean preparedWrite, boolean responseNeeded, int offset,
					byte[] value) {
				super.onCharacteristicWriteRequest(device, requestId,
						characteristic, preparedWrite, responseNeeded, offset,
						value);
				Log.i("BLEMainActivity:BluetoothGattServerCallback:onCharacteristicWriteRequest",
						"device : " + device.getAddress()
								+ " characteristic : "
								+ characteristic.getUuid() + "Value = "
								+ value.toString());
				updateText("Received button press event");

			}

			@Override
			public void onDescriptorReadRequest(BluetoothDevice device,
					int requestId, int offset,
					BluetoothGattDescriptor descriptor) {
				Log.i("BLEMainActivity:BluetoothGattServerCallback:onDescriptorReadRequest",
						"device : " + device.getAddress() + " request = "
								+ requestId + " offset = " + offset
								+ " descriptor = " + descriptor.getUuid());
			}

			@Override
			public void onDescriptorWriteRequest(BluetoothDevice device,
					int requestId, BluetoothGattDescriptor descriptor,
					boolean preparedWrite, boolean responseNeeded, int offset,
					byte[] value) {
				Log.i("BLEMainActivity:BluetoothGattServerCallback:onDescriptorWriteRequest",
						"device : " + device.getAddress() + " \n descriptor : "
								+ descriptor.getUuid());
			}

			@Override
			public void onExecuteWrite(BluetoothDevice device, int requestId,
					boolean execute) {
				Log.i("BLEMainActivity:BluetoothGattServerCallback:onExecuteWrite",
						"device : " + device.getAddress() + " request = "
								+ requestId + " execute = " + true);
			}
		};
		BluetoothGattServer gattServer = bluetoothManager.openGattServer(
				getApplicationContext(), mBluetoothGattServerCallback);
		BluetoothGattService service = new BluetoothGattService(
				IMMEDIATE_ALERT_SERVICE,
				BluetoothGattService.SERVICE_TYPE_PRIMARY);
		BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
				ALERT_LEVEL_CHAR,
				BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
				BluetoothGattCharacteristic.PERMISSION_WRITE);
		characteristic.setValue(ALERT_LEVEL_CHARACTERISTIC_VALUE,
				ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE,
				ALERT_LEVEL_CHARACTERISTIC_OFFSET);
		service.addCharacteristic(characteristic);
		gattServer.addService(service);
		Log.i("BLEMainActivity:setupGattServer",
				"Gatt server setup complete : " + gattServer.toString());

	}
}
