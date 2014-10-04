/*
 * GNU GENERAL PUBLIC LICENSE
 *
 * Android Paint is a Drawing Application for Android.
 * Copyright (C) 2014 Steve Jarvis
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * By Steve Jarvis. The core of this app was open source provided by Google.
 * I added functionality to make the painting full screen, shake for random colors,
 * save the picture and share it, and change the brush size.
 *
 * Version 1.2
 * - Added eye dropper
 * - Changed history to stack of paths/paints. Faster, more stable, and hopefully
 * 		won't cause memory crashes.
 * - Fixed dashed issue. When unchecked round gets checked.
 *
 * Version 2.0
 * - Allowed importing in free version
 * - Fixed bug so turning eraser on turns dropper off
 */

package com.sajarvis.paint;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

import yuku.ambilwarna.AmbilWarnaDialog;
import yuku.ambilwarna.OnAmbilWarnaListener;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import com.sajarvis.fingerpaint.R;

//TODO add import from camera option.
//TODO Make brush selector more graphic.
//TODO Look into tablet compatibility.
//TODO support orientation
//TODO asynctask pic saving

public class Main extends GraphicsActivity {

	//For change the app name. Used for directories. Happens in onCreate based on isFree
	private String dirName;

	//Paint vars and effects.
	private Paint mPaint;
	private MaskFilter mEmboss;
	private MaskFilter mBlur;
	private PorterDuffXfermode mBlendy;
	private DashPathEffect mDash;
	private boolean blendOn = false;	//To turn blendy back on after a color or size change

	//Accelerometer
	private SensorManager mSensorManager;
	private float mAccel; // acceleration apart from gravity
	private float mAccelCurrent; // current acceleration including gravity
	private float mAccelLast; // last acceleration including gravity

	//View
	DrawingView myView;	//The custom view
	int width, height;	//passed to custom view to make it the right size.

	//Prefs class is used for paint mostly.
	private Prefs prefs;

	//Animations
	private Animation slideIn, slideOut, openHide, adOut;

	//Panel stuff
	private LinearLayout drawing;
	private RelativeLayout panel, openPanel;
	private ImageView hide,show,brushChooser,colorChooser,save,send,import_pic,undo,redo,
	clear,eraser,shaken,grayBack,dropper;
	private SeekBar brushSizer;
	private TextView brushSize;

	//Store the file path each time it's stored. Also note whether the canvas
	//has changed since last save
	private File path = null;
	private boolean changed = false;

	//The history stack
	private Stack history;
	private int historyCount;

	//For background pic
	private ImageView backImage;

	//To ignore taps that don't move
	private boolean somethingWasActuallyDrawn;

	//Mark whether we're in eye dropper mode
	private boolean dropperOn;


	//onCreate set things up. Most of it happens in other methods called from here.
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		//Initiate prefs. All kinds of things use the Prefs class.
		prefs = new Prefs(this);

		//Declare the app name
		dirName = "FingerPainting";

		initiateWidgets();
		initiateAnims();
		setPaint();
		initiateAccel();
		setClickListeners();

		//Dropper function is not on
		dropperOn = false;

		//Get the height and width of display to make an appropriately sized custom view
		Display display = getWindowManager().getDefaultDisplay();
		width = display.getWidth();
		height = display.getHeight();

		//Initialize the history stack.
		history = new Stack(this, width, height);
		historyCount = -1;

		//Make a new custom view
		myView = new DrawingView(this, width, height);
		myView.setDrawingCacheEnabled(true);	//Allow to save drawing
		drawing.addView(myView);

		//Start off showing it hiding. Might help them know it's there.
		panel.startAnimation(openHide);

		//Sets the back to gray
		setBackground(false);	

		//Set the buttons disabled cause there's nothing in the stack
		updateUndoRedo();
	}

	//Make sure the accelerometer listener stops when the app does.
	@Override
	protected void onResume() {
		super.onResume();
		mSensorManager.registerListener(mSensorListener,
				mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	//Activities for when the app stops.
	@Override
	protected void onStop() {
		super.onStop();
		//Stop the motion listener
		mSensorManager.unregisterListener(mSensorListener);
		//Save preferences.
		mPaint.setXfermode(null);	//I don't want it saving transparent color paint.
		saveUsedOnExit();
	}

	/*
	 * Here I initiate all the stuff that could happen in onCreate. Called
	 * from onCreate.
	 */

	//Declare all the widgets
	public void initiateWidgets(){
		hide = (ImageView) findViewById(R.id.close_button);
		show = (ImageView) findViewById(R.id.open_button);
		brushChooser = (ImageView) findViewById(R.id.brush_chooser);
		colorChooser = (ImageView) findViewById(R.id.color_chooser);
		updateColorChooser(prefs.getLastColor());
		save = (ImageView) findViewById(R.id.save);
		send = (ImageView) findViewById(R.id.send);
		undo = (ImageView) findViewById(R.id.undo);
		redo = (ImageView) findViewById(R.id.redo);
		eraser = (ImageView) findViewById(R.id.eraser);
		clear = (ImageView) findViewById(R.id.clear_canvas);
		shaken = (ImageView) findViewById(R.id.shaken);
		backImage = (ImageView) findViewById(R.id.imported);
		grayBack = (ImageView) findViewById(R.id.gray);
		import_pic = (ImageView) findViewById(R.id.import_pic);
		panel = (RelativeLayout) findViewById(R.id.panel);
		openPanel = (RelativeLayout) findViewById(R.id.open_panel);
		drawing = (LinearLayout) findViewById(R.id.drawing);
		brushSizer = (SeekBar) findViewById(R.id.brush_sizer);
		brushSize = (TextView) findViewById(R.id.brush_current_size);
		dropper = (ImageView) findViewById(R.id.color_dropper);
	}

	//Declare animations
	public void initiateAnims(){
		slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in);
		slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out);
		openHide = AnimationUtils.loadAnimation(this, R.anim.open_hide);
		slideOut.setAnimationListener(collapseListener);
		openHide.setAnimationListener(collapseListener);

		AnimationUtils.loadAnimation(this, R.anim.slide_in_ads);
		adOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_ads);
		adOut.setAnimationListener(collapseListener);
	}

	//Set the accelerometer listen stuff
	public void initiateAccel(){
		//Accelerometer initialization. Accelerometer used for shaking random color.
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
		mAccel = 0.00f;
		mAccelCurrent = SensorManager.GRAVITY_EARTH;
		mAccelLast = SensorManager.GRAVITY_EARTH;
	}

	//Hides the panel after it slides off, otherwise it just shows up again.
	Animation.AnimationListener collapseListener=new Animation.AnimationListener() {
		public void onAnimationEnd(Animation animation) {
			panel.setVisibility(View.GONE);
			openPanel.setVisibility(View.VISIBLE);
			openPanel.startAnimation(slideIn);
		}
		public void onAnimationRepeat(Animation animation) {
			// not needed
		}
		public void onAnimationStart(Animation animation) {
			// not needed
		}
	};

	//Set the paint variables and preferences from last close
	public void setPaint(){
		//mPaint is the paint that will be used to draw the paths.
		mPaint = new Paint();
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setStrokeJoin(Paint.Join.ROUND);
		mPaint.setStrokeCap(Paint.Cap.ROUND);
		mPaint.setStrokeWidth(prefs.getLastSize());

		//To emboss, blur, src_atop, and dash.
		mEmboss = new EmbossMaskFilter(new float[] { 1, 1, 1 },0.4f, 6, 3.5f);
		mBlur = new BlurMaskFilter(8, BlurMaskFilter.Blur.NORMAL);
		mBlendy = new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP);
		mDash = new DashPathEffect(new float[]{10,40}, 1);

		//And set the brush color, style, filter, and effect from last time (from mPrefs)
		mPaint.setColor(prefs.getLastColor());
		setBrush(prefs.getLastFilter());
		setBrush(prefs.getLastStyle());
		setBrush(prefs.getLastEffect());

		//Set the progress bar right too
		brushSizer.setProgress((int)prefs.getLastSize());
		updateBrushSizeText((int)prefs.getLastSize());
	}

	//This just updates the text for the brush size.
	public void updateBrushSizeText(int size){
		brushSize.setText(""+size);
		brushSize.setTextSize((int)size/2);
	}

	//Set the onClickListeners for panel items.
	public void setClickListeners(){
		//Hide the panel and ad if free
		hide.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				panel.startAnimation(slideOut);
			}
		});
		//Show the panel and ad if free
		show.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				panel.setVisibility(View.VISIBLE);
				openPanel.setVisibility(View.GONE);
				panel.startAnimation(slideIn);
			}
		});
		//Start the brush chooser activity
		brushChooser.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				dropperOff();
				eraserOff();
				startBrushPicker();
			}
		});
		//Listener for the sizer. Change sizes.
		brushSizer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar arg0) {
				//Not needed
			}
			@Override
			public void onStartTrackingTouch(SeekBar arg0) {
				//Not needed
			}
			@Override
			public void onProgressChanged(SeekBar arg0, int progress, boolean fromUser) {
				mPaint.setStrokeWidth(progress);
				updateBrushSizeText(progress);
			}
		});
		//Pick un new color.
		colorChooser.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				dropperOff();	//Turn eye dropper off
				eraserOff();
				getColor();
			}
		});
		//Save the drawing.
		save.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				//Launches an activity to get the filename to save as.
				//Will be saved after they pick a valid file name.
				getFileName();
			}
		});
		//Send
		send.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				if(!changed && path != null){
					shareImage(path);
				}else{
					makeToast(getString(R.string.save_first));
				}
			}
		});
		//Import a picture
		import_pic.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				startImport();
			}
		});
		//Undo last edit
		undo.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				myView.undo();
				updateUndoRedo();
			}
		});
		//Redo that undo
		redo.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				myView.redo();
				updateUndoRedo();
			}
		});
		//Eraser
		eraser.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				//Turn dropper off if it's on
				dropperOff();

				if(mPaint.getXfermode() != null){
					mPaint.setXfermode(null);
					makeToast(getString(R.string.eraser_off));
				}else{
					mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
					makeToast(getString(R.string.eraser_on));
				}
			}
		});
		//Clear canvas
		clear.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				startClear();
				dropperOff();
				eraserOff();
			}
		});
		//Shake on/off
		shaken.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				if(prefs.shakeIsOn()){
					prefs.shakeOn(false);
					makeToast(getString(R.string.shake_off));
				}
				else {
					prefs.shakeOn(true);
					makeToast(getString(R.string.shake_on));
				}
			}
		});
		//The eye dropper function
		//Shake on/off
		dropper.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View arg0) {
				if(dropperOn){
					makeToast("Dropper Off");
					dropperOn=false;
				}else{
					makeToast("Dropper On");
					dropperOn=true;
				}
			}
		});
	}

	//Clear that background pic. If we imported another pic, set to black. If
	//we cleared another image, set it back to gray.
	//true if we're setting to black!
	public void setBackground(boolean setToBlack){
		if(setToBlack){
			grayBack.setVisibility(View.GONE);
			//backImage.setBackgroundResource(R.color.black);
		}else{
			backImage.setImageDrawable(null);	//Clear image
			grayBack.setVisibility(View.VISIBLE);
			//backImage.setBackgroundResource(R.color.gray);	//Set background gray
		}
		myView.invalidate();
	}

	/*
	 * Motion listener
	 */
	//Listen for accelerations.
	private final SensorEventListener mSensorListener = new SensorEventListener() {
		public void onSensorChanged(SensorEvent se) {
			float x = se.values[0];
			float y = se.values[1];
			float z = se.values[2];
			mAccelLast = mAccelCurrent;
			mAccelCurrent = (float) Math.sqrt((double) (x*x + y*y + z*z));
			float delta = mAccelCurrent - mAccelLast;
			mAccel = mAccel * 0.9f + delta; // perform low-cut filter
			//If the movement was great enough and if shake is turned on...
			if(mAccel > 3 && prefs.shakeIsOn()){
				myView.colorRandom();
			}
		}
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
	};

	/*
	 * Menu stuff.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = new MenuInflater(this);
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	//When a menu item is chosen
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.about:
			startActivity(new Intent(this, About.class));
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	//This updates the color of the colorChooser while keeping the nice rounded edges.
	public void updateColorChooser(int color){
		GradientDrawable drawable =
				(GradientDrawable) this.getResources().getDrawable(R.drawable.color_back);
		drawable.setColor(color);
		colorChooser.setBackgroundDrawable(drawable);
		colorChooser.postInvalidate();
	}

	//Update the undo/redo buttons after a touch, and after undo/redo, and clear.
	public void updateUndoRedo(){
		//if no more undos, disable undo button
		if(historyCount<1){
			undo.setColorFilter(R.color.disabled);
			undo.setClickable(false);
		}else{
			undo.setColorFilter(null);
			undo.setClickable(true);
		}
		//if no more redos, disable that one
		if(historyCount>=history.getSize()-1){
			redo.setColorFilter(R.color.disabled);
			redo.setClickable(false);
		}else{
			redo.setColorFilter(null);
			redo.setClickable(true);
		}
	}

	//Turn dropper off and make toast message
	public void dropperOff(){
		if(dropperOn){
			makeToast(getString(R.string.dropper_off));
		}
		dropperOn=false;
	}

	//Turn eraser on/off and make toast message
	public void eraserOff(){
		if(mPaint.getXfermode() != null){
			mPaint.setXfermode(null);
			makeToast(getString(R.string.eraser_off));
		}
	}

	//Sets the new color
	public void getColor(){
		// initialColor is the initially-selected color to be shown in the rectangle
		//on the left of the arrow. for example, 0xff000000 is black, 0xff0000ff is
		//blue. Please be aware of the initial 0xff which is the alpha.
		AmbilWarnaDialog dialog = new AmbilWarnaDialog(this, mPaint.getColor(),
				new OnAmbilWarnaListener() {
			@Override
			public void onOk(AmbilWarnaDialog dialog, int color) {
				// color is the color selected by the user.
				mPaint.setColor(color);
				updateColorChooser(color);
				if(blendOn){
					setBrush("keepBlendOn");
				}
			}
			@Override
			public void onCancel(AmbilWarnaDialog dialog) {
				// cancel was selected by the user
			}
		});
		dialog.show();
	}

	/*
	 * Saving and sharing the painting.
	 */
	//Save the photo only. Sharing is separate.
	private void savePhoto(String fileName) throws IOException {
		//Need to set contents of imageview to background of myView
		if(grayBack.getVisibility() == View.VISIBLE){
			myView.setBackgroundResource(R.color.gray);
		}else{
			myView.setBackgroundDrawable(backImage.getDrawable());
		}
		fileName = fileName.concat(".jpeg");
		//Var to check to see if the card is available
		String state = Environment.getExternalStorageState();
		//Check the state
		if(Environment.MEDIA_MOUNTED.equals(state)){	//It's available. Do it!
			Bitmap bMap = Bitmap.createBitmap(myView.getDrawingCache());
			//Gets the directory of local storage.
			String dir = Environment.getExternalStorageDirectory().toString();
			//Add my folder to the directory and create it.
			File completeDir = new File(dir+File.separator+dirName);
			completeDir.mkdirs();
			OutputStream fOut = null;
			File file = new File(completeDir,fileName);
			file.createNewFile();
			fOut = new FileOutputStream(file);
			bMap.compress(Bitmap.CompressFormat.JPEG, 90, fOut);
			fOut.flush();
			fOut.close();
			//Notify the user the file's been saved
			makeNoti(getString(R.string.noti_title),getString(R.string.noti_title),"Location: "+file.toString(),file);
			//So we know what to share and that we can.
			path = file;
			changed = false;
		}
		else if(Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)){	//Read only
			makeToast("Whoops, the media card is not available for writing.");
		}
		else{	//Don't know what's wrong, but it's wrong.
			makeToast("Problem! The media card is not available. Is it in the phone and mounted?");
		}
		//Clear the background of the image, otherwise it's dumb
		myView.setBackgroundDrawable(null);
	}

	//Share the image. Pass the file.
	public void shareImage(File file){
		Intent picShare = new Intent(android.content.Intent.ACTION_SEND);
		picShare.setType("image/jpeg");
		picShare.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
		startActivity(Intent.createChooser(picShare,"Send picture using:"));
	}

	/*
	 * Toast and notifications. The communication center.
	 */
	//Make a toast noti. Just pass the message.
	public void makeToast(String msg){
		Context context = getApplicationContext();
		int duration = Toast.LENGTH_SHORT;
		Toast toast = Toast.makeText(context, msg, duration);
		toast.show();
	}
	//Make a noti bar noti. Pass the scrolling text, the title, the real content, and the file for the intent.
	public void makeNoti(CharSequence tickerText, CharSequence contentTitle, CharSequence contentText, File file){
		//Get reference to notification manager
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

		//Instantiate the notification
		int icon = R.drawable.noti_icon;
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);

		// Make an intent that opens a image viewer
		Intent notiIntent = new Intent();
		notiIntent.setAction(android.content.Intent.ACTION_VIEW);
		notiIntent.setDataAndType(Uri.fromFile(file),"image/jpeg");
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notiIntent, 0);

		notification.flags = Notification.FLAG_AUTO_CANCEL;	//So it goes away

		Context context = this.getApplicationContext();
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

		//Send the notification
		mNotificationManager.notify(1, notification);
	}

	/*
	 * Here's all the startactivities for results.
	 */
	//Get a new brush size. Call the activity. Result passed to onActivityResult.
	public void startSizePicker(){
		Intent sizeChange = new Intent(this, Sizer.class);
		sizeChange.putExtra("brushSize", (int)mPaint.getStrokeWidth());
		startActivityForResult(sizeChange, 1);
	}
	//Start the brush selector.
	public void startBrushPicker(){
		//Make a extra of the current active brush stuff
		//It's a best attempt, getting all combinations right is tricky.
		Intent brushChange = new Intent(this, Brushes.class);
		if(mPaint.getPathEffect() == mDash){
			brushChange.putExtra("dash", true);
		}else{
			brushChange.putExtra("dash", false);
		}
		if(mPaint.getStyle() == Paint.Style.STROKE && mPaint.getPathEffect() == null){
			brushChange.putExtra("round", true);
		}else{
			brushChange.putExtra("round", false);
		}
		if(mPaint.getMaskFilter() == mEmboss){
			brushChange.putExtra("emboss", true);
		}else{
			brushChange.putExtra("emboss", false);
		}
		if(mPaint.getMaskFilter() == mBlur){
			brushChange.putExtra("blur", true);
		}else{
			brushChange.putExtra("blur", false);
		}
		brushChange.putExtra("blendy", blendOn);
		if(mPaint.getStyle() == Paint.Style.FILL){
			brushChange.putExtra("crazyfill", true);
		}else{
			brushChange.putExtra("crazyfill", false);
		}
		startActivityForResult(brushChange, 2);
	}
	//Confirms clear bitmap
	public void startClear(){
		Intent chooseClear = new Intent(this, Clear.class);
		startActivityForResult(chooseClear, 3);
	}
	//Gets the filename to save.
	public void getFileName(){
		Intent getName = new Intent(this, FileName.class);
		getName.putExtra("dirName", dirName);
		startActivityForResult(getName, 4);
	}
	//Start the gallery to import a picture
	public void startImport(){
		Intent getUri = new Intent(Intent.ACTION_GET_CONTENT);
		getUri.setType("image/*");
		startActivityForResult(getUri, 5);
	}
	//Launch the prompt
	//	public void startPrompt(){
	//		Intent prompt = new Intent(this, Prompt.class);
	//		startActivityForResult(prompt, 6);
	//	}

	//Gets results from any activities started for result.
	protected void onActivityResult(int requestCode, int resultCode, Intent intent){
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode){
		case 1:	//Is the size picker
			if(resultCode==RESULT_OK){
				int size = intent.getIntExtra("returnKey", 12);
				mPaint.setStrokeWidth(size);
				if(blendOn){
					setBrush("keepBlendOn");
				}
			}
			break;
		case 2:	//This is the brush style chooser
			if(resultCode==RESULT_OK){
				if(intent.getBooleanExtra("returnRound", false)){
					setBrush("round");
				}
				if(intent.getBooleanExtra("returnDash", false)){
					setBrush("dash");
				}
				if(intent.getBooleanExtra("returnEmboss", false)){
					setBrush("emboss");
				}else{	//emboss wasn't checked
					mPaint.setMaskFilter(null);
				}
				if(intent.getBooleanExtra("returnBlur", false)){
					setBrush("blur");
				}else if(mPaint.getMaskFilter() != mEmboss){
					mPaint.setMaskFilter(null);
				}
				if(intent.getBooleanExtra("returnStroke", false)){
					setBrush("stroke");
				}
				if(intent.getBooleanExtra("returnBlendy", false)){
					setBrush("keepBlendOn");
					blendOn = true;
				}else{
					blendOn=true;
					setBrush("blendy");
					mPaint.setAlpha(255);	//No more transparency!
				}
			}
			break;
		case 3:	//Confirmation to clear screen.
			if(resultCode==RESULT_OK){
				if(intent.getStringExtra("clear").equals("drawing")){
					myView.clearDrawing();
					if(dropperOn){
						dropperOff();
					}
				}
				else if(intent.getStringExtra("clear").equals("picture")){
					setBackground(false);
				}
				else if(intent.getStringExtra("clear").equals("all")){
					myView.clearDrawing();
					setBackground(false);
					if(dropperOn){
						dropperOff();
					}
				}
				else{	//Cancel. Do nothing.

				}
			}
			break;
		case 4:	//Get a filename to save as.
			if(resultCode==RESULT_OK){
				String fName = intent.getStringExtra("FileName");
				if(!fName.equals("blank")){
					try {
						savePhoto(fName);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}else{
				}
			}
			break;
		case 5:	//Import picture
			if(resultCode==RESULT_OK){
				//Just the selected image is all we need. decodeUri worries about
				//Changing it into an appropriate bitmap.
				Uri selectedImage = intent.getData();
				setBitmap(selectedImage);
			}
			break;
		case 6:	//Results from prompt
			if(resultCode == RESULT_OK){
				if(!intent.getBooleanExtra("showAgain", true)){
					//Don't show the prompt again
					prefs.promptState(false);
				}
			}
			break;
		}
	}

	//From Uri to Bitmap
	public void setBitmap(Uri selectedImage){
		Bitmap bmap = null;	//Will contain the pixels
		try {
			bmap = decodeUri(selectedImage);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		//Set it as the background. picBack method will make it mutable.
		myView.picBack(bmap);
		myView.invalidate();
	}

	//Downsizes bitmap. It will be scaled when it's set in mBitmap, but if we don't
	//do this too it'll run out of memory.
	private Bitmap decodeUri(Uri selectedImage) throws FileNotFoundException {
		//Decode image size.
		BitmapFactory.Options options = new BitmapFactory.Options();
		//inJustDecodeBounds reads the dimensions without actually importing, so it won't crash.
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage),
				null, options);

		//The new size we want to scale to
		final int REQUIRED_SIZE = width;

		//Find the correct scale value. It should be the power of 2.
		int width_tmp=options.outWidth, height_tmp=options.outHeight;
		int scale=1;
		while(true){
			//Changed this to && instead of ||. Out of memory errors.
			if(width_tmp/2<REQUIRED_SIZE && height_tmp/2<REQUIRED_SIZE)
				break;
			width_tmp/=2;
			height_tmp/=2;
			scale*=2;
		}
		// Decode with inSampleSize
		BitmapFactory.Options optScale = new BitmapFactory.Options();
		optScale.inSampleSize = scale;	//If scale is smaller than 1 it won't do anything.
		return BitmapFactory.decodeStream(
				getContentResolver().openInputStream(selectedImage), null, optScale);
	}

	//setBrush runs after the brush chooser dialog runs.
	//Set the brush to whatever brush was returned from there.
	public void setBrush(String brush){
		if(brush.equals("round")){
			//Clear it all
			mPaint.setStyle(Paint.Style.STROKE);	//Turn off crazy fill (or erase)
			mPaint.setPathEffect(null);	//Turn off dashed.
			if(blendOn){
				setBrush("keepBlendOn");
			}
		}
		else if(brush.equals("dash")){
			mPaint.setPathEffect(mDash);
			mPaint.setStyle(Paint.Style.STROKE);	//Turn off fill

			if(blendOn){
				setBrush("keepBlendOn");
			}
		}
		else if(brush.equals("emboss")){
			mPaint.setMaskFilter(mEmboss);

			if(blendOn){
				setBrush("keepBlendOn");
			}
		}
		else if(brush.equals("blur")){
			mPaint.setMaskFilter(mBlur);

			if(blendOn){
				setBrush("keepBlendOn");
			}
		}
		//Blendy is from the brush selection, should toggle.
		else if(brush.equals("blendy")){
			if (!blendOn){
				mPaint.setXfermode(mBlendy);
				mPaint.setAlpha(0x80);
				blendOn = true;
			}else{
				mPaint.setXfermode(null);
				blendOn = false;
			}
		}
		//KeepBlendOn is from calling after color or brush change. Otherwise is overwritten.
		//Not toggle, just make sure it's on.
		else if (brush.equals("keepBlendOn")){
			mPaint.setXfermode(mBlendy);
			mPaint.setAlpha(0x80);
		}
		else if(brush.equals("stroke")){
			mPaint.setStyle(Paint.Style.FILL);
			mPaint.setPathEffect(null);	//Turn off dash

			if(blendOn){
				setBrush("keepBlendOn");
			}
		}
		//else it's probably loading null from prefs, don't do anything.
	}

	//Save color & brush prefs on exit
	public void saveUsedOnExit(){
		mPaint.setAlpha(255);	//No transparency for saving.
		prefs.setColor(mPaint.getColor());
		prefs.setSize(mPaint.getStrokeWidth());
		prefs.setStyle(getPaintStyle());
		prefs.setFilter(getPaintFilter());
		prefs.setEffect(getPaintEffect());
	}
	//Returns the value of the paint style. Either round or stroke.
	public String getPaintStyle(){
		//Either fill or not. Nevermind how I call it the opposite.
		if(mPaint.getStyle() == Paint.Style.FILL){
			return "stroke";
		}
		return "round";
	}
	//Returns blur, emboss, or neither.
	public String getPaintFilter(){
		if (mPaint.getMaskFilter() == mBlur) {
			return "blur";
		}
		else if (mPaint.getMaskFilter() == mEmboss){
			return "emboss";
		}
		else return "null";
	}
	//Dashed on or off.
	public String getPaintEffect(){
		if(mPaint.getPathEffect() == mDash){
			return "dash";
		}
		return "null";
	}

	/*
	 * This is the custom view. Just a drawing surface.
	 */
	public class DrawingView extends View {
		private Bitmap  mBitmap;
		private Canvas  mCanvas;
		private Path    mPath;
		private Paint   mBitmapPaint;

		//Constructor
		public DrawingView(Context c, int width, int height) {
			super(c);

			mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			mCanvas = new Canvas(mBitmap);
			mPath = new Path();
			mBitmapPaint = new Paint(Paint.DITHER_FLAG);

			//Save the clear state.
			storePp(true);
		}

		//Shake things up and pick a random color.
		public void colorRandom() {
			Random rnd = new Random();
			mPaint.setARGB(255, rnd.nextInt(255), rnd.nextInt(255), rnd.nextInt(255));
			mPaint.setXfermode(null);	//Stop erasing.
			if(blendOn){
				setBrush("keepBlendOn");
			}
			//Set the color icon
			updateColorChooser(mPaint.getColor());
		}

		//Clear the drawing
		public void clearDrawing(){
			mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			mCanvas = new Canvas(mBitmap);
			mPath = new Path();
			invalidate();
			//Reset the history stuff.
			historyCount = -1;
			history.clear();
			//Save the clear state.
			storePp(true);
			//Disable both undo/redo
			updateUndoRedo();
			myView.invalidate();
		}

		//Set the background to a scaled bitmap
		public void picBack(Bitmap bmap){
			//Set the background to black. Helps hide the fact that there's spaces.
			Main.this.setBackground(true);

			//Catch a null pointer. Seems to happen if the image is corrupted
			try{
				Bitmap b = Bitmap.createScaledBitmap(bmap, width, height, false);
				backImage.setImageBitmap(b);
				invalidate();
			}catch(NullPointerException e){
				makeToast(getString(R.string.import_error));
			}
		}

		//Save the path and paint. Boss just allows an override for the
		//somethingWasActuallyDrawn for constructor and clear
		public void storePp(boolean boss){
			//Save it! Need to copy the path and paint, otherwise it won't work.
			//Seems to be a recurring theme, this inexplicable linkage.
			if(somethingWasActuallyDrawn || boss){
				historyCount++;
				if(historyCount > history.getDepth()){
					historyCount = history.getDepth();
				}
				//Not sure how to do this without declaring a new paint. They're
				//otherwise linked somehow. Recurring theme.
				//TODO ask about that. Reference vs Value?
				Paint temp = new Paint();
				temp.set(mPaint);
				history.add(historyCount,new PathPaint(new Path(mPath), temp));
			}
		}

		//Undo the last change. Involves getting the last id from the stack and
		//replacing it for the current bitmap. Decrement the count.
		public void undo(){
			if(historyCount>0){	//Else we're blank
				historyCount--;

				//Clear bitmap
				mBitmap = Bitmap.createBitmap(history.getBase());
				mCanvas = new Canvas(mBitmap);

				//We are undoing or redoing
				mPath.reset();

				//Loop through all the paths in the history and draw them.
				for(int i=0; i<=historyCount; i++){
					//Draw to mBitmap
					mCanvas.drawPath(history.get(i).getPath(), history.get(i).getPaint());
				}

				//redraw
				invalidate();
			}else{
				makeToast("End of undo history.");
			}
		}

		//Redoing the last undo. If there is one.
		public void redo(){
			if(history.getSize() > historyCount+1){	//Then continue, we're in range.
				historyCount++;

				//Clear bitmap
				mBitmap = Bitmap.createBitmap(history.getBase());
				mCanvas = new Canvas(mBitmap);

				//We are undoing or redoing
				mPath.reset();

				//Loop through all the paths in the history and draw them.
				for(int i=0; i<=historyCount; i++){
					//Draw to mBitmap
					mCanvas.drawPath(history.get(i).getPath(), history.get(i).getPaint());
				}

				invalidate();
			}else{
				makeToast("End of redo history.");
			}
		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			super.onSizeChanged(w, h, oldw, oldh);
		}

		//Draws the bitmap and paths.
		@Override
		protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);

			//Regular drawing stuff. Needs to be done regardless
			canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
			canvas.drawPath(mPath, mPaint);
		}

		private float mX, mY;
		private static final float TOUCH_TOLERANCE = 4;

		//Record the path for the touch start, move, and stop.
		private void touch_start(float x, float y) {
			mPath.reset();
			mPath.moveTo(x, y);
			mX = x;
			mY = y;
		}
		private void touch_move(float x, float y) {
			float dx = Math.abs(x - mX);
			float dy = Math.abs(y - mY);
			if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
				mPath.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
				mX = x;
				mY = y;
				somethingWasActuallyDrawn = true;
			}
		}
		private void touch_up() {
			mPath.lineTo(mX, mY);
			// commit the path to our offscreen
			mCanvas.drawPath(mPath, mPaint);

			storePp(false);

			// kill this so we don't double draw
			mPath.reset();
		}
		//Record the actual touch events to paint
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			float x = event.getX();
			float y = event.getY();
			if(dropperOn){
				int colorTouched = mBitmap.getPixel((int)x,(int)y);
				if(colorTouched != 0){
					mPaint.setColor(colorTouched);
					//Set the color icon
					updateColorChooser(mPaint.getColor());
				}
			}else{
				changed = true;	//We know it's been modified since last save.

				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					somethingWasActuallyDrawn = false;
					//Record the touch
					touch_start(x, y);
					myView.invalidate();
					break;
				case MotionEvent.ACTION_MOVE:
					touch_move(x, y);
					myView.invalidate();
					break;
				case MotionEvent.ACTION_UP:
					touch_up();
					myView.invalidate();
					updateUndoRedo();
					break;
				}
			}
			return true;
		}
	}
}
