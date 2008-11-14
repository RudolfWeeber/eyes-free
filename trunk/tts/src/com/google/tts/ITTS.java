/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /home/raman/src/eyes-free/tts/src/com/google/tts/ITTS.aidl
 */
package com.google.tts;
import java.lang.String;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Binder;
import android.os.Parcel;
// Declare the interface.

public interface ITTS extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.google.tts.ITTS
{
private static final java.lang.String DESCRIPTOR = "com.google.tts.ITTS";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an ITTS interface,
 * generating a proxy if needed.
 */
public static com.google.tts.ITTS asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
com.google.tts.ITTS in = (com.google.tts.ITTS)obj.queryLocalInterface(DESCRIPTOR);
if ((in!=null)) {
return in;
}
return new com.google.tts.ITTS.Stub.Proxy(obj);
}
public android.os.IBinder asBinder()
{
return this;
}
public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_setEngine:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.setEngine(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_speak:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _arg1;
_arg1 = data.readInt();
java.lang.String[] _arg2;
_arg2 = data.createStringArray();
this.speak(_arg0, _arg1, _arg2);
reply.writeNoException();
return true;
}
case TRANSACTION_stop:
{
data.enforceInterface(DESCRIPTOR);
this.stop();
reply.writeNoException();
return true;
}
case TRANSACTION_addSpeech:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
int _arg2;
_arg2 = data.readInt();
this.addSpeech(_arg0, _arg1, _arg2);
reply.writeNoException();
return true;
}
case TRANSACTION_addSpeechFile:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
this.addSpeechFile(_arg0, _arg1);
reply.writeNoException();
return true;
}
case TRANSACTION_getVersion:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getVersion();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.google.tts.ITTS
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
public void setEngine(java.lang.String selectedEngine) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(selectedEngine);
mRemote.transact(Stub.TRANSACTION_setEngine, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void speak(java.lang.String text, int queueMode, java.lang.String[] params) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(text);
_data.writeInt(queueMode);
_data.writeStringArray(params);
mRemote.transact(Stub.TRANSACTION_speak, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void stop() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_stop, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void addSpeech(java.lang.String text, java.lang.String packageName, int resId) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(text);
_data.writeString(packageName);
_data.writeInt(resId);
mRemote.transact(Stub.TRANSACTION_addSpeech, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void addSpeechFile(java.lang.String text, java.lang.String filename) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(text);
_data.writeString(filename);
mRemote.transact(Stub.TRANSACTION_addSpeechFile, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public int getVersion() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getVersion, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_setEngine = (IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_speak = (IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_stop = (IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_addSpeech = (IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_addSpeechFile = (IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_getVersion = (IBinder.FIRST_CALL_TRANSACTION + 5);
}
public void setEngine(java.lang.String selectedEngine) throws android.os.RemoteException;
public void speak(java.lang.String text, int queueMode, java.lang.String[] params) throws android.os.RemoteException;
public void stop() throws android.os.RemoteException;
public void addSpeech(java.lang.String text, java.lang.String packageName, int resId) throws android.os.RemoteException;
public void addSpeechFile(java.lang.String text, java.lang.String filename) throws android.os.RemoteException;
public int getVersion() throws android.os.RemoteException;
}
