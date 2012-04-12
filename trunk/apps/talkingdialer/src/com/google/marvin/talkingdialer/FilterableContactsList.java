
package com.google.marvin.talkingdialer;

import java.util.ArrayList;
import java.util.ListIterator;

public class FilterableContactsList {
    private ArrayList<ContactEntry> fullList;

    private ArrayList<ContactEntry> filteredList;

    private ListIterator<ContactEntry> filteredListIter;

    @SuppressWarnings("unchecked")
    public FilterableContactsList(ArrayList<String> names) {
        fullList = new ArrayList<ContactEntry>();
        for (int i = 0; i < names.size(); i++) {
            fullList.add(new ContactEntry(names.get(i), i));
        }
        filteredList = (ArrayList<ContactEntry>) fullList.clone();
        filteredListIter = filteredList.listIterator();
    }

    public ContactEntry next() {
        if (filteredList.size() < 1) {
            return null;
        }
        if (!filteredListIter.hasNext()) {
            filteredListIter = filteredList.listIterator();
        }
        return filteredListIter.next();
    }

    public ContactEntry previous() {
        if (filteredList.size() < 1) {
            return null;
        }
        if (!filteredListIter.hasPrevious()) {
            filteredListIter = filteredList.listIterator(filteredList.size());
        }
        return filteredListIter.previous();
    }

    @SuppressWarnings("unchecked")
    public boolean filter(String partialName) {
        filteredList = (ArrayList<ContactEntry>) fullList.clone();
        filteredListIter = filteredList.listIterator();
        if (partialName.length() > 0) {
            String lcPN = partialName.toLowerCase();
            filteredList = new ArrayList<ContactEntry>();
            for (int i = 0; i < fullList.size(); i++) {
                ContactEntry entry = fullList.get(i);
                if (entry.name != null) {
                    String lcName = entry.name.toLowerCase();
                    if (lcName.startsWith(lcPN)) {
                        filteredList.add(entry);
                    }
                }
            }
            filteredListIter = filteredList.listIterator();
        }
        if (filteredList.size() > 0) {
            return true;
        }
        return false;
    }

}
