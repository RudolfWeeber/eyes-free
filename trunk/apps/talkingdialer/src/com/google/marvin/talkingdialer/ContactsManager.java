// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.marvin.talkingdialer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.ContactsContract.StatusUpdates;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts.Data;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.CursorLoader;
import android.support.v4.util.LruCache;
import android.util.Log;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Manager for TalkingDialer contacts. Loads all phone contacts via query to
 * Contacts URI with filter for contacts with phone numbers. Given reduced
 * contacts list, uses contact lookup keys to query individual contact details
 * asynchronously. Contact details are stored in a cache and reloaded on demand.
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public class ContactsManager {

    private static final Uri URI_PHONE = Contacts.CONTENT_URI;

    private static final Uri URI_EMAIL = Email.CONTENT_URI;

    private static final String SORTORDER = Contacts.DISPLAY_NAME
            + " COLLATE NOCASE ASC";

    private Context context;
    private CursorLoader loader;
    private Cursor mCursor;

    private final boolean isVideoSupported;
    private final int mode;

    private LruCache<String, Contact> contactsCache;

    private static class ContactQuery {
        final static String[] COLUMNS = new String[] {
                Contacts.DISPLAY_NAME,
                Contacts.CONTACT_PRESENCE, Contacts.CONTACT_CHAT_CAPABILITY,
                Contacts.LOOKUP_KEY, Contacts._ID };

        public final static int DISPLAY_NAME = 0;
        public final static int CONTACT_PRESENCE = 1;
        public final static int CONTACT_CAPABILITY = 2;
        public final static int CONTACT_LOOKUP = 3;
        public final static int CONTACT_ID = 4;

    }

    private static class EmailQuery {
        final static String[] COLUMNS = new String[] {
                Contacts.DISPLAY_NAME,
                Email.ADDRESS,
                Email.RAW_CONTACT_ID,
                Email.LOOKUP_KEY
        };
        public final static int DISPLAY_NAME = 0;
        public final static int ADDRESS = 1;
        public final static int CONTACT_ID = 2;
        public final static int CONTACT_LOOKUP = 3;
    }

    public class ContactData {
        public final boolean isNumber;
        public final String data;
        public final String type;
        public final int id;

        public ContactData(String data, String type, boolean isNumber, int id) {
            if (data == null) {
                data = "";
            }
            if (type == null) {
                type = "";
            }
            this.data = data;
            this.isNumber = isNumber;
            this.type = type;
            this.id = id;
        }

        @Override
        public int hashCode() {
            return data.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return data.equals(((ContactData) other).data);

        }
    }

    public class Contact {
        protected final String name;
        protected final String key;
        protected final int presence;
        protected final int capability;

        private LinkedList<ContactData> contactData;
        private ListIterator<ContactData> iter;

        public Contact(String name, String key, int presence, int capability) {
            if (name == null) {
                name = "";
            }
            this.name = name;
            this.key = key;
            this.presence = presence;
            this.capability = capability;
            contactData = new LinkedList<ContactData>();
        }

        public void resetIter() {
            iter = contactData.listIterator();
        }

        public void addData(HashSet<ContactData> data) {
            contactData.addAll(data);
            iter = contactData.listIterator();
        }

        public void addData(ContactData data) {
            contactData.add(data);
            iter = contactData.listIterator();
        }

        public ContactData prevData() {
            if (iter.hasPrevious()) {
                return iter.previous();
            }
            return null;
        }

        public ContactData nextData() {
            if (iter.hasNext()) {
                return iter.next();
            }
            return null;
        }

        public boolean hasData() {
            return iter.hasNext();
        }
    }

    /**
     * @param context
     */
    public ContactsManager(Context context, int mode, boolean isVideoSupported) {
        this.context = context;
        this.isVideoSupported = isVideoSupported;
        this.mode = mode;

        String filter = "";
        Uri uri;
        String[] proj;

        if (mode == TalkingDialer.DIAL || mode == TalkingDialer.SELECT_PHONE) {
            // Phone filter
            filter = Contacts.HAS_PHONE_NUMBER + "=1";
            // Also video calls if available
            if (mode == TalkingDialer.DIAL && isVideoSupported) {
                filter += " OR (" + Contacts.CONTACT_CHAT_CAPABILITY + " > -1"
                        + " AND " + Contacts.HAS_PHONE_NUMBER + "=1 )";
            }
            uri = URI_PHONE;
            proj = ContactQuery.COLUMNS;
        } else {
            // Selecting from all emails
            uri = URI_EMAIL;
            proj = EmailQuery.COLUMNS;
        }

        loader = new CursorLoader(context, uri, proj, filter,
                null, SORTORDER);
        mCursor = loader.loadInBackground();

        boolean hasFirst = mCursor != null && mCursor.moveToFirst();
        if (hasFirst) {
            // Limit to something reasonable (average size of Contact ~300 bytes)
            // So this should be << 1 MB
            int cacheSize = 100;
            contactsCache = new LruCache<String, Contact>(cacheSize);
            new ContactDataTask(filter, uri, proj).execute();
        }

    }

    /**
     * Gets phone numbers for given contact, eliminating duplicates with set
     */
    public HashSet<ContactData> getPhoneNumbers(Cursor cursor) {

        final String filter;
        final String[] projection;
        final Uri entityUri;

        if (Build.VERSION.SDK_INT > 11) {

            projection = new String[] {
                    Contacts.Entity.DATA_ID,
                    Phone.NUMBER, Phone.TYPE, Data.MIMETYPE, Phone.RAW_CONTACT_ID };

            filter = Data.MIMETYPE + "= '" + Phone.CONTENT_ITEM_TYPE + "'";

            final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
                    cursor.getLong(ContactQuery.CONTACT_ID));

            entityUri = Uri.withAppendedPath(baseUri,
                    Contacts.Entity.CONTENT_DIRECTORY);

        } else {

            filter = Contacts.DISPLAY_NAME + " = '" + cursor.getString(ContactQuery.DISPLAY_NAME)
                    + "'";

            projection = new String[] {
                    Contacts.DISPLAY_NAME,
                    Phone.NUMBER, Phone.TYPE, Data.MIMETYPE, Phone.RAW_CONTACT_ID };

            entityUri = Phone.CONTENT_URI;

        }

        AsyncTaskLoader<HashSet<ContactData>> loader
        = new AsyncTaskLoader<HashSet<ContactData>>(context) {

                @Override
            public HashSet<ContactData> loadInBackground() {
                HashSet<ContactData> numbers = new HashSet<ContactData>();
                Cursor cursor = getContext().getContentResolver().query(
                        entityUri, projection, filter, null, null);
                if (cursor.moveToFirst()) {
                    do {

                        cursor.getInt(4);
                        String normalizedNumber = cursor.getString(1)
                                .replaceAll("[^0-9*#,;]", "");
                        String type = "";
                        if (!cursor.isNull(2)) {
                            switch (cursor.getInt(2)) {
                                case Phone.TYPE_HOME:
                                    type = context.getString(R.string.home);
                                    break;
                                case Phone.TYPE_MOBILE:
                                    type = context.getString(R.string.cell);
                                    break;
                                case Phone.TYPE_WORK:
                                    type = context.getString(R.string.work);
                                    break;
                                default:
                                    type = context.getString(R.string.phone);
                                    break;
                            }
                        }
                        numbers.add(new ContactData(normalizedNumber, type,
                                true, cursor.getInt(4)));

                    } while (cursor.moveToNext());
                }
                cursor.close();
                return numbers;
            }

        };
        return loader.loadInBackground();
    }

    /**
     * Gets email addresses for given contact, eliminating duplicates with set
     */
    public HashSet<ContactData> getEmails(Cursor cursor) {

        final String filter;
        final String[] projection;
        final Uri entityUri;

        if (Build.VERSION.SDK_INT > 11) {

            projection = new String[] {
                    Contacts.Entity.DATA_ID,
                    Email.ADDRESS, Email.CHAT_CAPABILITY, Data.MIMETYPE, Email.RAW_CONTACT_ID };

            final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
                    cursor.getLong(ContactQuery.CONTACT_ID));
            entityUri = Uri.withAppendedPath(baseUri,
                    Contacts.Entity.CONTENT_DIRECTORY);

            filter = Email.CHAT_CAPABILITY + " > -1 AND "
                    + Data.MIMETYPE + " = '" + Email.CONTENT_ITEM_TYPE + "'";

        } else {

            filter = Email.CHAT_CAPABILITY + " > -1 AND " + Contacts.DISPLAY_NAME + " = '"
                    + cursor.getString(ContactQuery.DISPLAY_NAME) + "'";

            projection = new String[] {
                    Contacts.DISPLAY_NAME,
                    Email.ADDRESS, Email.CHAT_CAPABILITY, Data.MIMETYPE, Email.RAW_CONTACT_ID };

            entityUri = Email.CONTENT_URI;

        }

        AsyncTaskLoader<HashSet<ContactData>> loader = new AsyncTaskLoader<HashSet<ContactData>>(
                context) {

                @Override
            public HashSet<ContactData> loadInBackground() {

                HashSet<ContactData> emails = new HashSet<ContactData>();
                Cursor cursor = getContext().getContentResolver().query(
                        entityUri, projection, filter, null, null);
                if (cursor.moveToFirst()) {
                    do {
                        if (!cursor.isNull(ContactQuery.CONTACT_CAPABILITY)) {
                            int capability = cursor
                                    .getInt(ContactQuery.CONTACT_CAPABILITY);
                            int videoChat = capability
                                    & StatusUpdates.CAPABILITY_HAS_CAMERA;
                            boolean vChatCapable = videoChat == StatusUpdates.CAPABILITY_HAS_CAMERA;
                            if (vChatCapable) {

                                final String address = cursor.getString(1);
                                final String[] parts = address.split("@");
                                String type = "";
                                if (parts.length > 1) {
                                    type = parts[1];
                                }
                                emails.add(new ContactData(address, type, false, cursor.getInt(4)));
                            }
                        }

                    } while (cursor.moveToNext());
                }
                cursor.close();
                return emails;
            }

        };
        return loader.loadInBackground();
    }

    /*
     * Cursor utility functions
     */
    public int getPos() {
        return mCursor.getPosition();
    }

    public boolean hasContacts() {
        return mCursor != null && mCursor.moveToFirst();
    }

    public boolean isAfterLast() {
        return mCursor.isAfterLast();
    }

    /**
     * Attempts to grab current contact from cache, reloads otherwise
     */
    public Contact getCurrentContact() {
        Contact lookUp = contactsCache.get(mCursor
                .getString(ContactQuery.CONTACT_LOOKUP));
        if (lookUp != null) {
            lookUp.resetIter();
            return lookUp;
        }
        // force-load if not available yet
        return loadContact(mCursor);
    }

    /**
     * Moves to first contact with data
     */
    public Contact reset() {
        mCursor.moveToFirst();
        Contact contact = getCurrentContact();
        // move to first contact with data
        while (!mCursor.isLast()
                && (contact == null || !contact.hasData())) {
            mCursor.moveToNext();
            contact = getCurrentContact();
        }
        return contact;
    }

    public Contact getNextContact() {
        if (!mCursor.moveToNext()) {
            mCursor.moveToFirst();
        }
        Contact nextContact = getCurrentContact();
        if (nextContact == null) {
            return getNextContact();
        }
        return nextContact;
    }

    public Contact getPreviousContact() {
        if (!mCursor.moveToPrevious()) {
            mCursor.moveToLast();
        }
        Contact prevContact = getCurrentContact();
        if (prevContact == null) {
            return getPreviousContact();
        }
        ContactData data;
        // Move to last data position
        do {
            data = prevContact.nextData();
        } while (data != null);
        return prevContact;
    }

    /*
     * Functions for search database that are cheaper than above
     */
    public String getNextName() {
        if (!mCursor.moveToNext()) {
            mCursor.moveToFirst();
        }
        return mCursor.getString(ContactQuery.DISPLAY_NAME);
    }

    public String getPreviousName() {
        if (!mCursor.moveToPrevious()) {
            mCursor.moveToLast();
        }
        return mCursor.getString(ContactQuery.DISPLAY_NAME);
    }

    /**
     * Loads contact data from entity database
     *
     * @param cursor
     * @return newContact
     */
    private Contact loadContact(Cursor cursor) {
        if (mode != TalkingDialer.SELECT_EMAIL) {

            Contact newContact = new Contact(
                    cursor.getString(ContactQuery.DISPLAY_NAME),
                    cursor.getString(ContactQuery.CONTACT_LOOKUP),
                    cursor.getInt(ContactQuery.CONTACT_PRESENCE),
                    cursor.getInt(ContactQuery.CONTACT_CAPABILITY));

            newContact.addData(getPhoneNumbers(cursor));
            if (isVideoSupported) {
                newContact.addData(getEmails(cursor));
            }

            contactsCache.put(newContact.key, newContact);

            return newContact;

        } else {

            final String address = mCursor.getString(EmailQuery.ADDRESS);
            final String name = mCursor.getString(EmailQuery.DISPLAY_NAME);

            if (address != null && name != null && !name.equals(address)) {

                final String[] parts = address.split("@");
                String type = "";
                if (parts.length > 1) {
                    type = parts[1];
                }

                Contact newContact = new Contact(
                        mCursor.getString(EmailQuery.DISPLAY_NAME),
                        mCursor.getString(EmailQuery.CONTACT_LOOKUP), 0, 0);

                newContact.addData(new ContactData(
                        address, type, false, mCursor.getInt(EmailQuery.CONTACT_ID)));

                contactsCache.put(newContact.key, newContact);

                return newContact;
            }
        }

        return null;
    }

    /**
     * This is an asynchronous task that queries individual contact entities for
     * phone, email, and chat data
     */
    private class ContactDataTask extends AsyncTask<Void, Void, Void> {
        Cursor cursor;

        public ContactDataTask(String filter, Uri uri, String[] proj) {
            super();
            CursorLoader asyncLoader = new CursorLoader(context, uri,
                    proj, filter, null, SORTORDER);
            cursor = asyncLoader.loadInBackground();
            Log.i("CONTACTSMANAGER", "BEGIN CACHE SIZE "+contactsCache.size());
        }

        @Override
        public Void doInBackground(Void... params) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            do {
                loadContact(cursor);
            } while (cursor.moveToNext());
            return null;
        }

        @Override
        public void onPostExecute(Void test) {
            Log.i("CONTACTSMANAGER", "END CACHE SIZE "+contactsCache.size());
        }
    }

}
