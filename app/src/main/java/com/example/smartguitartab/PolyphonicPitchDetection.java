/*
	This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

	N.B.  the above text was copied from http://www.gnu.org/licenses/gpl.html
	unmodified. I have not attached a copy of the GNU license to the source...

    Copyright (C) 2011-2012 Timo Rantalainen
 */

/*
Written by Timo Rantalainen tjrantal@gmail.com 2010 (C++ version) - 2012 (Java version)
Based on Anssi Klapuri's (list of publications http://www.cs.tut.fi/~klap/iiro/ and http://www.elec.qmul.ac.uk/people/anssik/publications.htm) congress publication
Klapuri, A., " Multiple fundamental frequency estimation by summing harmonic amplitudes," 7th International Conference on Music Information Retrieval (ISMIR-06), Victoria, Canada, Oct. 2006.
http://www.cs.tut.fi/sgn/arg/klap/klap2006ismir.pdf
 and doctoral thesis:
Klapuri, A. " Signal processing methods for the automatic transcription of music," Ph.D. thesis, Tampere University of Technology, Finland, April 2004.
 http://www.cs.tut.fi/sgn/arg/klap/phd/klap_phd.pdf

Contributions from other people taken from the internet (in addition to Java-tutorials for GUI, sound capture etc.)
FFT-transform

Required class files (in addition to this one..).
ReadStratecFile.java		//Stratec pQCT file reader
DrawImage.java				//Visualize image in a panel
SelectROI.java
AnalyzeRoi.java			//Analysis calculations

JAVA compilation:
javac -cp '.:' ui/PolyphonicPitchDetection.java \
Capture/Capture.java \
DrawImage/DrawImage.java \
Analysis/Analysis.java \
Analysis/Complex.java \
Analysis/FFT.java \
Analysis/Functions.java \
Analysis/Klapuri.java
JAR building:
jar cfe PolyphonicPitchDetection.jar ui.PolyphonicPitchDetection ui DrawImage Analysis Capture

 */
package com.example.smartguitartab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

public class PolyphonicPitchDetection {


	public int fftWindow = 4096;	/*FFT window width ~0.1 s -> Max ~600 bpm*/
	public float samplingRate = 44100;
	public static int imWidth =800;
	public static int imHeight =250;
	public static int harmonics = 20;
	public static int w;
	public static int h;
	static int traces = 2;		/*how many traces are we plotting...*/
	public double[] cb;			/*Klapuri whitening ranges*/
	public ArrayList<Double>[] Hb;	/*filter bank for whitening*/
	public ArrayList<Integer>[] hbIndices;	/*filter bank indices for whitening*/
	public double[] freq;		/*FFT fequency bins*/
	public double[] f0cands;	/*Klapuri F0 candidates*/
	public ArrayList<Integer>[] f0index;		/*Klapuri F0 candidate indices*/
	public ArrayList<Integer>[] f0indHarm;		/*Klapuri F0 candidate indices harmonics*/

	int bitSelection;
	int stereo;
	int bitDepth;


	public PolyphonicPitchDetection(){ /*Constructor*/
		/*Create constant arrays for Klapuri*/
		cb = new double[32];
		/*CB filterbank always the same values, could be included from somewhere...*/
		for (int b = 0;b<32;++b){
			cb[b] = 229.0*(Math.pow(10.0,(((double) (b+1.0))/21.4))-1.0); //frequency division
		}
		/*Frequencies, always the same after capture init...
			captured signal will be zero padded to twice its length, so valid fft bins are equal to original epoch length
		 */
		freq = new double[(int) Math.floor((double) fftWindow)];
		for (int b = 0;b<Math.floor((double) fftWindow);++b){
			freq[b] = (double) b*(double)(samplingRate/2.0)/(double) fftWindow;
		}

		/*Create filter bank*/
		Hb = new ArrayList[30];
		hbIndices = new ArrayList[30];
		for (int i = 1;i<31;++i){
			Hb[i-1] = new ArrayList<Double>();
			hbIndices[i-1] = new ArrayList<Integer>();
			int kk=Klapuri.ind(freq,cb[i-1]);
			while (freq[kk] <= cb[i+1]){
				hbIndices[i-1] .add(kk);
				if (freq[kk]<=cb[i]){
					Hb[i-1].add(1-Math.abs(cb[i]-freq[kk])/(cb[i]-cb[i-1]));
				}else{
					Hb[i-1].add(1-Math.abs(cb[i]-freq[kk])/(cb[i+1]-cb[i]));
				}
				++kk;
			}
		}

		/*
		 *Create candidate frequencies here (http://www.phy.mtu.edu/~suits/NoteFreqCalcs.html)
		 *Five octaves of candidate notes. Use quarter a half-step to get out of tune freqs
		 *Lowest freq (f0) = 55.0 Hz, A three octaves below A above the middle C
		 */
		double f0Init = 55;	//Hz
		double a = Math.pow(2.0,(1.0/12.0));
		f0cands = new double[5*12*4];	//5 octaves, 12 half-steps per octave, quarter half-steps
		for (int kk = 0;kk<f0cands.length;++kk){
			f0cands[kk] = f0Init*Math.pow(a,((double)kk)/4.0);
		}

		/*
		 *Pre-calculate frequency bins for  a given f0 candidate
		 */
		f0index = new ArrayList[f0cands.length];
		f0indHarm = new ArrayList[f0cands.length];
		double halfBinWidth= ((double)samplingRate/(double) fftWindow)/2.0;
		for (int k =0;k<f0index.length;++k){
			f0index[k] = new ArrayList();
			f0indHarm[k] = new ArrayList();
			for (int h =0; h< harmonics;++h){
				ArrayList<Integer> tempInd =find(freq,f0cands[k]*((double)h+1.0)-halfBinWidth,f0cands[k]*((double)h+1.0)+halfBinWidth);
				f0index[k].addAll(tempInd);
				for (int t = 0;t<tempInd.size();++t){
					f0indHarm[k] .add(h+1);
				}
			}
		}

		bitDepth = 16;
		bitSelection = bitDepth/8;
		stereo = 1; /*Capture mono*/
	}

	public PolyphonicPitchDetection(int buffersize, float samplingRate, int bitDepth){ /*Constructor*/
		/* Set configuration of audio sample */
		fftWindow = buffersize;
		samplingRate = samplingRate;
		bitDepth = bitDepth;
		/*Create constant arrays for Klapuri*/
		cb = new double[32];
		/*CB filterbank always the same values, could be included from somewhere...*/
		for (int b = 0;b<32;++b){
			cb[b] = 229.0*(Math.pow(10.0,(((double) (b+1.0))/21.4))-1.0); //frequency division
		}
		/*Frequencies, always the same after capture init...
			captured signal will be zero padded to twice its length, so valid fft bins are equal to original epoch length
		 */
		freq = new double[(int) Math.floor((double) fftWindow)];
		for (int b = 0;b<Math.floor((double) fftWindow);++b){
			freq[b] = (double) b*(double)(samplingRate/2.0)/(double) fftWindow;
		}

		/*Create filter bank*/
		Hb = new ArrayList[30];
		hbIndices = new ArrayList[30];
		for (int i = 1;i<31;++i){
			Hb[i-1] = new ArrayList<Double>();
			hbIndices[i-1] = new ArrayList<Integer>();
			int kk=Klapuri.ind(freq,cb[i-1]);
			while (freq[kk] <= cb[i+1]){
				hbIndices[i-1] .add(kk);
				if (freq[kk]<=cb[i]){
					Hb[i-1].add(1-Math.abs(cb[i]-freq[kk])/(cb[i]-cb[i-1]));
				}else{
					Hb[i-1].add(1-Math.abs(cb[i]-freq[kk])/(cb[i+1]-cb[i]));
				}
				++kk;
			}
		}

		/*
		 *Create candidate frequencies here (http://www.phy.mtu.edu/~suits/NoteFreqCalcs.html)
		 *Five octaves of candidate notes. Use quarter a half-step to get out of tune freqs
		 *Lowest freq (f0) = 55.0 Hz, A three octaves below A above the middle C
		 */
		double f0Init = 55;	//Hz
		double a = Math.pow(2.0,(1.0/12.0));
		f0cands = new double[5*12*4];	//5 octaves, 12 half-steps per octave, quarter half-steps
		for (int kk = 0;kk<f0cands.length;++kk){
			f0cands[kk] = f0Init*Math.pow(a,((double)kk)/4.0);
		}

		/*
		 *Pre-calculate frequency bins for  a given f0 candidate
		 */
		f0index = new ArrayList[f0cands.length];
		f0indHarm = new ArrayList[f0cands.length];
		double halfBinWidth= ((double)samplingRate/(double) fftWindow)/2.0;
		for (int k =0;k<f0index.length;++k){
			f0index[k] = new ArrayList();
			f0indHarm[k] = new ArrayList();
			for (int h =0; h< harmonics;++h){
				ArrayList<Integer> tempInd =find(freq,f0cands[k]*((double)h+1.0)-halfBinWidth,f0cands[k]*((double)h+1.0)+halfBinWidth);
				f0index[k].addAll(tempInd);
				for (int t = 0;t<tempInd.size();++t){
					f0indHarm[k] .add(h+1);
				}
			}
		}

		bitSelection = bitDepth/8;
		stereo = 1; /*Capture mono*/
	}

	public Vector<Double> run(short[] buffer)
	{
		try{

			//	int bufferSize = fftWindow*bitSelection*stereo;	

			//	byte buffer[] = new byte[bufferSize];


			if (bitSelection ==1){
				//mainProgram.rawFigure.drawImage(buffer,mainProgram.imWidth,mainProgram.imHeight);
				/*Add pitch detection here for 8 bit, not implemented...*/
			}
			if (bitSelection ==2){

				short[] data = new short[fftWindow*bitSelection*stereo];
				//data = double2shortarray(buffer);
				data = buffer;
				//mainProgram.rawFigure.drawImage(data,mainProgram.imWidth,mainProgram.imHeight);
				/*Add pitch detection here for 16 bit*/
				//System.out.println("To analysis");
				Analysis analysis = new Analysis(data,this);	//FFT + klapuri analysis
				//System.out.println("Out of analysis");

				//mainProgram.whitenedFftFigure.plotTrace(analysis.klapuri.whitened,analysis.whitenedMaximum,1024);

				return analysis.klapuri.f0s;
				/*
								mainProgram.fftFigure.drawImage(analysis.amplitudes,mainProgram.imWidth,mainProgram.imHeight,analysis.maximum);
				 */
				//mainProgram.whitenedFftFigure.drawImage(analysis.klapuri.whitened,analysis.whitenedMaximum,analysis.klapuri.f0s);
			}
			//mainProgram.rawFigure.paintImmediately(0,0,mainProgram.imWidth,mainProgram.imHeight);

		} catch  (Exception err){	System.err.println("Error: " + err.getMessage());}
		return null;
	}

	public static short[] byteArrayToShortArray(byte[] arrayIn){
		short[] shortArray = new short[arrayIn.length/2];
		for (int i = 0;i<shortArray.length;++i){
			shortArray[i] = (short) (((((int) arrayIn[2*i+1]) & 0XFF)<< 8) | (((int) arrayIn[2*i]) & 0XFF));
		}
		return shortArray;
	}

	private ArrayList<Integer> find(double[] arr, double lower, double upper){
		ArrayList<Integer> b = new ArrayList<Integer>();
		for (int i = 0; i<arr.length;++i){
			if (arr[i]>=lower && arr[i] <=upper){
				b.add(i);
			}
		}
		return b;
	}

	public short[] double2shortarray(float[] datain)
	{
		short[] dataout = new short[datain.length];
		for (int i=0 ; i< datain.length ; i++)
			dataout [i] = (short) datain[i];//byteArrayToShortArray(buffer);

		return dataout;
	}
}


