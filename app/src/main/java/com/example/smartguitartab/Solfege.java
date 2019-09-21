package com.example.smartguitartab;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class Solfege {
    static int  A4 = 440;
    static String noteName[]  = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    static String stdTunning[] = {"E4", "B3", "G3", "D3", "A2", "E2"};
    static double C0=A4*Math.pow(2, -4.75);

    public static double log2(double num)
    {
        return (Math.log(num)/Math.log(2));
    }

    /*
     *  Return notes with its octave number from its frequency
     */
    public static String getPitchFromFrequence(double freq)
    {
        int h = (int) Math.round(12*log2(freq/C0));
        int octave = h / 12;
        int n = h % 12;

        return noteName[n] + octave;
    }


    /*
     *  Return difference between two notes, in semitones
     */
    public static int compareNotes(String note1, String note2)
    {
        return (Arrays.asList(noteName).indexOf(note2.substring(0, note2.length() - 1)) -
                Arrays.asList(noteName).indexOf(note1.substring(0, note1.length() - 1))) + 12*
                ( Integer.parseInt(note2.substring(note2.length() - 1)) -
                        Integer.parseInt(note1.substring(note1.length() - 1)));
    }

    /*
     *  Return position of note on string
     *
    public static Map<Integer, Integer> findTabValue(String note)
    {
        int semitonesDiff;
        Map<Integer, Integer> tabValue = new HashMap<>();

        for(int i=0 ; i < stdTunning.length ; i++)
        {
            semitonesDiff = compareNotes(stdTunning[i], note);
            if ( semitonesDiff >=0) {
                tabValue.put(i, semitonesDiff++);
                return tabValue;
            }
        }
        if(tabValue.isEmpty())
        {
            tabValue.put(-1, -1);
            return tabValue;
        }

    }*/

    /*
     *  Return position of each notes on string
     *  Map < String number, position on the string >
     */
    public static Map<Integer, Integer> findTabValue(Vector<String> notes)
    {
        Vector<String> stringNotes = new Vector<String>(Arrays.asList(stdTunning));;

        Map<Integer, Integer> tabValues = new HashMap<>();
        int semitonesDiff;

        for(int i=0 ; i < notes.size() ; i++)
        {
            for(int j=0 ; j < stringNotes.size() ; j++)
            {
                semitonesDiff = compareNotes(stringNotes.get(j), notes.get(i));
                if ( semitonesDiff >=0)
                {
                    tabValues.put(j+1, semitonesDiff);
                    stringNotes.remove(j);
                }
            }
        }
        if (!tabValues.isEmpty()) return tabValues;
        else return null;
    }

}
