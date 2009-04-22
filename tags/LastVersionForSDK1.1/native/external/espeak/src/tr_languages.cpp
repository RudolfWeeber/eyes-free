/***************************************************************************
 *   Copyright (C) 2005 to 2007 by Jonathan Duddington                     *
 *   email: jonsd@users.sourceforge.net                                    *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 3 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, see:                                 *
 *               <http://www.gnu.org/licenses/>.                           *
 ***************************************************************************/

#include "StdAfx.h"

#include <stdio.h>
#include <ctype.h>
#include <wctype.h>
#include <stdlib.h>
#include <string.h>
#include <locale.h>

#include <wctype.h>

#include "speak_lib.h"
#include "speech.h"
#include "phoneme.h"
#include "synthesize.h"
#include "translate.h"
#include "tr_languages.h"



#define L_qa   0x716100
#define L_grc  0x677263   // grc  Ancient Greek
#define L_jbo  0x6a626f   // jbo  Lojban
#define L_zhy  0x7a6879   // zhy


#define OFFSET_GREEK  0x380
#define OFFSET_CYRILLIC 0x420
#define OFFSET_DEVANAGARI  0x900
#define OFFSET_TAMIL  0xb80
#define OFFSET_KOREAN 0x1100


static const unsigned int replace_cyrillic_latin[] = 
	{0x430,'a',
	0x431,'b',
	0x446,'c',
	0x45b,0x107,
	0x447,0x10d,
	0x45f,'d'+(0x17e<<16),
	0x455,'d'+('z'<<16),
	0x434,'d',
	0x452,0x111,
	0x435,'e',
	0x444,'f',
	0x433,'g',
	0x445,'h',
	0x438,'i',
	0x458,'j',
	0x43a,'k',
	0x459,'l'+('j'<<16),
	0x43b,'l',
	0x43c,'m',
	0x45a,'n'+('j'<<16),
	0x43d,'n',
	0x43e,'o',
	0x43f,'p',
	0x440,'r',
	0x441,'s',
	0x448,0x161,
	0x442,'t',
	0x443,'u',
	0x432,'v',
	0x437,'z',
	0x436,0x17e,
	0x453,0x111,
	0x45c,0x107,
0};  // ѓ  ѕ  ќ


void SetupTranslator(Translator *tr, const short *lengths, const unsigned char *amps)
{//==================================================================================
	if(lengths != NULL)
		memcpy(tr->stress_lengths,lengths,sizeof(tr->stress_lengths));
	if(amps != NULL)
		memcpy(tr->stress_amps,amps,sizeof(tr->stress_amps));
}


Translator *SelectTranslator(const char *name)
{//===========================================
	int name2 = 0;
	Translator *tr;

	// convert name string into a word of up to 4 characters, for the switch()
	while(*name != 0)
		name2 = (name2 << 8) + *name++;


	switch(name2)
	{
	case L('a','f'):
		{
			static const short stress_lengths_af[8] = {170,140, 220,220,  0, 0, 250,270};
			tr = new Translator();
			SetupTranslator(tr,stress_lengths_af,NULL);

			tr->langopts.stress_rule = 0;
			tr->langopts.vowel_pause = 0x30;
			tr->langopts.param[LOPT_DIERESES] = 1;
			tr->langopts.param[LOPT_PREFIXES] = 1;
			SetLetterVowel(tr,'y');  // add 'y' to vowels
		
			tr->langopts.numbers = 0x8d1 + NUM_ROMAN;
			tr->langopts.accents = 1;
		}
		break;

	case L('c','y'):   // Welsh
		{
			static const short stress_lengths_cy[8] = {170,220, 180,180, 0, 0, 250,270};
			static const unsigned char stress_amps_cy[8] = {17,15, 18,18, 0,0, 22,20 };    // 'diminished' is used to mark a quieter, final unstressed syllable

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_cy,stress_amps_cy);

			tr->charset_a0 = charsets[14];   // ISO-8859-14
//			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable
			tr->langopts.stress_rule = 2;
//			tr->langopts.intonation_group = 4;

			// 'diminished' is an unstressed final syllable
			tr->langopts.stress_flags =  0x6 | 0x10; 
			tr->langopts.unstressed_wd1 = 0;
			tr->langopts.unstressed_wd2 = 2;
			tr->langopts.param[LOPT_SONORANT_MIN] = 120;  // limit the shortening of sonorants before short vowels

			tr->langopts.numbers = 0x401;

			SetLetterVowel(tr,'w');  // add letter to vowels and remove from consonants
			SetLetterVowel(tr,'y');
		}
		break;

	case L('d','a'):  // Danish
		{
			static const unsigned char stress_amps_da[] = {16,16, 20,20, 20,24, 24,22 };
			static const short stress_lengths_da[8] = {160,140, 200,200, 0,0, 220,210};
			tr = new Translator();
			SetupTranslator(tr,stress_lengths_da,stress_amps_da);

			tr->langopts.stress_rule = 0;
			SetLetterVowel(tr,'y');
//			tr->langopts.numbers = 0x11849;
		}
		break;


	case L('d','e'):
		{
			static const short stress_lengths_de[8] = {150,130, 190,190,  0, 0, 260,275};
			tr = new Translator();
			tr->langopts.stress_rule = 0;
			tr->langopts.word_gap = 0x8;   // don't use linking phonemes
			tr->langopts.vowel_pause = 0x30;
			tr->langopts.param[LOPT_PREFIXES] = 1;
			memcpy(tr->stress_lengths,stress_lengths_de,sizeof(tr->stress_lengths));
		
			tr->langopts.numbers = 0x11419 + NUM_ROMAN;
			SetLetterVowel(tr,'y');
		}
		break;

	case L('e','n'):
		tr = new Translator_English();
		break;

	case L('e','l'):   // Greek
	case L_grc:        // Ancient Greek
		{
			static const short stress_lengths_el[8] = {155, 180,  210, 210,  0, 0,  270, 300};
			static const unsigned char stress_amps_el[8] = {15,12, 20,20, 20,24, 24,22 };    // 'diminished' is used to mark a quieter, final unstressed syllable

			// character codes offset by 0x380
			static const char el_vowels[] = {0x10,0x2c,0x2d,0x2e,0x2f,0x30,0x31,0x35,0x37,0x39,0x3f,0x45,0x49,0x4a,0x4b,0x4c,0x4d,0x4e,0x4f,0};
			static const char el_fvowels[] = {0x2d,0x2e,0x2f,0x35,0x37,0x39,0x45,0x4d,0}; // ε η ι υ  έ ή ί ύ
			static const char el_voiceless[]= {0x38,0x3a,0x3e,0x40,0x42,0x43,0x44,0x46,0x47,0};  // θ κ ξ π ς σ τ φ χ 
			static const char el_consonants[]={0x32,0x33,0x34,0x36,0x38,0x3a,0x3b,0x3c,0x3d,0x3e,0x40,0x41,0x42,0x43,0x44,0x46,0x47,0x48,0};
			static const wchar_t el_char_apostrophe[] = {0x3c3,0};  // σ

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_el,stress_amps_el);

			tr->charset_a0 = charsets[7];   // ISO-8859-7
			tr->char_plus_apostrophe = el_char_apostrophe;

			tr->letter_bits_offset = OFFSET_GREEK;
			memset(tr->letter_bits,0,sizeof(tr->letter_bits));
			SetLetterBits(tr,LETTERGP_A,el_vowels);
			SetLetterBits(tr,LETTERGP_B,el_voiceless);
			SetLetterBits(tr,LETTERGP_C,el_consonants);
			SetLetterBits(tr,LETTERGP_Y,el_fvowels);    // front vowels: ε η ι υ

			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable
			tr->langopts.stress_rule = 2;
			tr->langopts.stress_flags = 0x6;  // mark unstressed final syllables as diminished
			tr->langopts.unstressed_wd1 = 0;
			tr->langopts.unstressed_wd2 = 2;
			tr->langopts.param[LOPT_SONORANT_MIN] = 130;  // limit the shortening of sonorants before short vowels

			tr->langopts.numbers = 0x309;
			tr->langopts.numbers2 = 0x2;   // variant form of numbers before thousands

			if(name2 == L_grc)
			{
				// ancient greek
				tr->langopts.param[LOPT_UNPRONOUNCABLE] = 1;
			}
		}
		break;

	case L('e','o'):
		{
			static const short stress_lengths_eo[8] = {145, 180,  200, 190,    0,   0,  300, 320};
			static const unsigned char stress_amps_eo[] = {16,14, 20,20, 20,24, 24,22 };
			static const wchar_t eo_char_apostrophe[2] = {'l',0};
		
			tr = new Translator();
			SetupTranslator(tr,stress_lengths_eo,stress_amps_eo);

			tr->charset_a0 = charsets[3];  // ISO-8859-3
			tr->char_plus_apostrophe = eo_char_apostrophe;

			tr->langopts.vowel_pause = 1;
			tr->langopts.stress_rule = 2;
			tr->langopts.stress_flags =  0x6 | 0x10; 
			tr->langopts.unstressed_wd1 = 1;
			tr->langopts.unstressed_wd2 = 2;

			tr->langopts.numbers = 0x1409 + NUM_ROMAN;
		}
		break;

	case L('e','s'):   // Spanish
		{
			static const short stress_lengths_es[8] = {170, 200,  180, 180,  0, 0,  220, 250};
			static const unsigned char stress_amps_es[8] = {16,12, 18,18, 20,20, 20,20 };    // 'diminished' is used to mark a quieter, final unstressed syllable

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_es,stress_amps_es);

			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable
			tr->langopts.stress_rule = 2;

			// stress last syllable if it doesn't end in vowel or "s" or "n"
			// 'diminished' is an unstressed final syllable
			tr->langopts.stress_flags = 0x200 | 0x6 | 0x10; 
			tr->langopts.unstressed_wd1 = 0;
			tr->langopts.unstressed_wd2 = 2;
			tr->langopts.param[LOPT_SONORANT_MIN] = 120;  // limit the shortening of sonorants before short vowels

			tr->langopts.numbers = 0x529 + NUM_ROMAN;
		}
		break;


	case L('f','i'):   // Finnish
		{
			static const unsigned char stress_amps_fi[8] = {18,16, 22,22, 20,22, 22,22 };
			static const short stress_lengths_fi[8] = {150,180, 200,200, 0,0, 210,250};

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_fi,stress_amps_fi);

			tr->langopts.stress_rule = 0;
			tr->langopts.stress_flags = 0x56;  // move secondary stress from light to a following heavy syllable
			tr->langopts.param[LOPT_IT_DOUBLING] = 1;
			tr->langopts.long_stop = 130;

			tr->langopts.numbers = 0x1809;
			SetLetterVowel(tr,'y');
			tr->langopts.max_initial_consonants = 2;
			tr->langopts.spelling_stress = 1;
			tr->langopts.intonation_group = 3;  // less intonation, don't raise pitch at comma
		}
		break;

	case L('f','r'):  // french
		{
			static const short stress_lengths_fr[8] = {190, 170,  190, 200,  0, 0,  235, 240};
			static const unsigned char stress_amps_fr[8] = {18,16, 20,20, 20,22, 22,21 };

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_fr,stress_amps_fr);
			tr->langopts.stress_rule = 3;      // stress on final syllable
			tr->langopts.stress_flags = 0x0024;  // don't use secondary stress
			tr->langopts.param[LOPT_IT_LENGTHEN] = 1;    // remove lengthen indicator from unstressed syllables

			tr->langopts.numbers = 0x1509 + 0x8000 + NUM_NOPAUSE | NUM_ROMAN;
			SetLetterVowel(tr,'y');
		}
		break;

	case L('h','i'):
		{
			static const char dev_consonants2[] = {0x02,0x03,0x58,0x59,0x5a,0x5b,0x5c,0x5d,0x5e,0x5f};
			static const short stress_lengths_hi[8] = {190, 190,  210, 210,  0, 0,  230, 250};
			static const unsigned char stress_amps_hi[8] = {17,14, 20,19, 20,24, 24,22 };

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_hi,stress_amps_hi);
			tr->charset_a0 = charsets[19];   // ISCII
			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable

			tr->langopts.stress_rule = 6;      // stress on last heaviest syllable, excluding final syllable
			tr->langopts.stress_flags =  0x10004;   // use 'diminished' for unstressed final syllable
			tr->langopts.numbers = 0x011;
			tr->langopts.numbers2 = 0x100;
			tr->letter_bits_offset = OFFSET_DEVANAGARI;

			memset(tr->letter_bits,0,sizeof(tr->letter_bits));
			SetLetterBitsRange(tr,LETTERGP_A,0x06,0x14);   // vowel letters
			SetLetterBitsRange(tr,LETTERGP_B,0x3e,0x4d);   // vowel signs + virama
			SetLetterBitsRange(tr,LETTERGP_C,0x15,0x39);   // the main consonant range
			SetLetterBits(tr,LETTERGP_C,dev_consonants2);  // additional consonants
			tr->langopts.param[LOPT_UNPRONOUNCABLE] = 1;   // disable check for unpronouncable words
		}
		break;


	case L('h','r'):   // Croatian
	case L('b','s'):   // Bosnian
	case L('s','r'):   // Serbian
		{
			static const unsigned char stress_amps_hr[8] = {16,16, 20,20, 20,24, 24,22 };
			static const short stress_lengths_hr[8] = {180,160, 200,200, 0,0, 220,230};
			static const short stress_lengths_sr[8] = {160,150, 200,200, 0,0, 250,260};

			tr = new Translator();

			if(name2 == L('s','r'))
				SetupTranslator(tr,stress_lengths_sr,stress_amps_hr);
			else
				SetupTranslator(tr,stress_lengths_hr,stress_amps_hr);
			tr->charset_a0 = charsets[2];   // ISO-8859-2

			tr->langopts.stress_rule = 0;
			tr->langopts.stress_flags = 0x10;  
			tr->langopts.param[LOPT_REGRESSIVE_VOICING] = 0x3;
 			tr->langopts.max_initial_consonants = 5;
			tr->langopts.spelling_stress = 1;
			tr->langopts.accents = 1;

			tr->langopts.numbers = 0x140d + 0x4000 + NUM_ROMAN_UC;
			tr->langopts.numbers2 = 0x4a;  // variant numbers before thousands,milliards
			tr->langopts.replace_chars = replace_cyrillic_latin;

			SetLetterVowel(tr,'y');
			SetLetterVowel(tr,'r');
		}
		break;


	case L('h','u'):   // Hungarian
		{
			static const unsigned char stress_amps_hu[8] = {17,17, 19,19, 20,24, 24,22 };
			static const short stress_lengths_hu[8] = {185,195, 195,190, 0,0, 210,220};

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_hu,stress_amps_hu);
			tr->charset_a0 = charsets[2];   // ISO-8859-2

			tr->langopts.vowel_pause = 0x20;
			tr->langopts.stress_rule = 0;
			tr->langopts.stress_flags = 0x8036;
			tr->langopts.unstressed_wd1 = 2;
//			tr->langopts.param[LOPT_REGRESSIVE_VOICING] = 0x4;  // don't propagate over word boundaries
			tr->langopts.param[LOPT_IT_DOUBLING] = 1;
			tr->langopts.param[LOPT_COMBINE_WORDS] = 99;  // combine some prepositions with the following word

			tr->langopts.numbers = 0x1009 + NUM_ROMAN;
			SetLetterVowel(tr,'y');
			tr->langopts.spelling_stress = 1;
SetLengthMods(tr,3);  // all equal
		}
		break;

	case L('i','d'):   // Indonesian
		{
			static const short stress_lengths_id[8] = {160, 200,  180, 180,  0, 0,  220, 240};
			static const unsigned char stress_amps_id[8] = {16,18, 18,18, 20,22, 22,21 };
			tr = new Translator();
			SetupTranslator(tr,stress_lengths_id,stress_amps_id);
			tr->langopts.stress_rule = 2;
			tr->langopts.numbers = 0x1009 + NUM_ROMAN;
			tr->langopts.stress_flags =  0x6 | 0x10; 
			tr->langopts.accents = 2;  // "capital" after letter name
		}
		break;

	case L('i','s'):   // Icelandic
		{
			static const unsigned char stress_amps_is[] = {16,16, 20,20, 20,24, 24,22 };
			static const short stress_lengths_is[8] = {180,160, 200,200, 0,0, 240,250};
			static const wchar_t is_lettergroup_B[] = {'c','f','h','k','p','t','x',0xfe,0};  // voiceless conants, including 'þ'  ?? 's'

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_is,stress_amps_is);
			tr->langopts.stress_rule = 0;
			tr->langopts.stress_flags = 0x10;
			tr->langopts.param[LOPT_IT_LENGTHEN] = 0x11;    // remove lengthen indicator from unstressed vowels
			tr->langopts.param[LOPT_REDUCE] = 2;

			ResetLetterBits(tr,0x18);
			SetLetterBits(tr,4,"kpst");   // Letter group F
			SetLetterBits(tr,3,"jvr");    // Letter group H
			tr->letter_groups[1] = is_lettergroup_B;
			SetLetterVowel(tr,'y');
			tr->langopts.numbers = 0x8e9;
			tr->langopts.numbers2 = 0x2;
		}
		break;

	case L('i','t'):   // Italian
		{
			static const short stress_lengths_it[8] = {150, 140,  180, 180,  0, 0,  270, 320};
			static const unsigned char stress_amps_it[8] = {15,14, 19,19, 20,24, 24,22 };

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_it,stress_amps_it);

			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable
			tr->langopts.stress_rule = 2;
			tr->langopts.vowel_pause = 1;
			tr->langopts.unstressed_wd1 = 2;
			tr->langopts.unstressed_wd2 = 2;
			tr->langopts.param[LOPT_IT_LENGTHEN] = 2;    // remove lengthen indicator from unstressed or non-penultimate syllables
			tr->langopts.param[LOPT_IT_DOUBLING] = 2;    // double the first consonant if the previous word ends in a stressed vowel
			tr->langopts.param[LOPT_SONORANT_MIN] = 130;  // limit the shortening of sonorants before short vowels
			tr->langopts.param[LOPT_REDUCE] = 1;        // reduce vowels even if phonemes are specified in it_list
			tr->langopts.numbers = 0x2709 + NUM_ROMAN;
		}
		break;

	case L('j','a'):  // TEST
			tr = new Translator();
			tr->langopts.param[LOPT_UNPRONOUNCABLE] = 1;   // disable check for unpronouncable words
		break;

	case L_jbo:   // Lojban
		{
			static const short stress_lengths_jbo[8] = {185,170, 200,200, 0,0, 290,300};
			static const wchar_t jbo_punct_within_word[] = {'.',',','\'',0x2c8,0};  // allow period and comma within a word, also stress marker (from LOPT_SYLLABLE_CAPS)
			tr = new Translator();
			SetupTranslator(tr,stress_lengths_jbo,NULL);
			tr->langopts.stress_rule = 2;
			tr->langopts.vowel_pause = 0x20c;  // pause before a word which starts with a vowel, or after a word which ends in a consonant
			tr->punct_within_word = jbo_punct_within_word;
			tr->langopts.param[LOPT_SYLLABLE_CAPS] = 1;  // capitals indicate stressed syllables
			SetLetterVowel(tr,'y');
		}
		break;

	case L('k','o'):   // Korean, TEST
		{
			static const char ko_ivowels[] = {0x63,0x64,0x67,0x68,0x6d,0x72,0x74,0x75,0};  // y and i vowels
			static const char ko_voiced[] = {0x02,0x05,0x06,0xab,0xaf,0xb7,0xbc,0};  // voiced consonants, l,m,n,N
			tr = new Translator();

			tr->letter_bits_offset = OFFSET_KOREAN;
			memset(tr->letter_bits,0,sizeof(tr->letter_bits));
			SetLetterBitsRange(tr,LETTERGP_A,0x61,0x75);
			SetLetterBits(tr,LETTERGP_Y,ko_ivowels);
			SetLetterBits(tr,LETTERGP_G,ko_voiced);

			tr->langopts.stress_rule = 8;   // ?? 1st syllable if it is heavy, else 2nd syllable
			tr->langopts.param[LOPT_UNPRONOUNCABLE] = 1;   // disable check for unpronouncable words
			tr->langopts.numbers = 0x0401;
		}
		break;

	case L('k','u'):   // Kurdish
		{
			static const unsigned char stress_amps_ku[8] = {18,18, 20,20, 20,24, 24,22 };
			static const short stress_lengths_ku[8] = {180,180, 190,180, 0,0, 230,240};

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_ku,stress_amps_ku);
			tr->charset_a0 = charsets[9];   // ISO-8859-9 - Latin5

			tr->langopts.stress_rule = 7;   // stress on the last syllable, before any explicitly unstressed syllable

			tr->langopts.numbers = 0x100461;
			tr->langopts.max_initial_consonants = 2;
		}
		break;

	case L('l','a'):  //Latin
		{
			tr = new Translator();
			tr->charset_a0 = charsets[4];   // ISO-8859-4, includes a,e,i,o,u-macron
			tr->langopts.stress_rule = 2;
			tr->langopts.stress_flags = 0x20;
			tr->langopts.unstressed_wd1 = 0;
			tr->langopts.unstressed_wd2 = 2;
			tr->langopts.param[LOPT_DIERESES] = 1;
			tr->langopts.numbers = 0x1 + NUM_ROMAN;
			tr->langopts.max_roman = 5000;
		}
		break;

	case L('m','k'):   // Macedonian
		{
			static wchar_t vowels_cyrillic[] = {0x440,  // also include 'р' [R]
				 0x430,0x435,0x438,0x439,0x43e,0x443,0x44b,0x44d,0x44e,0x44f,0x450,0x451,0x456,0x457,0x45d,0x45e,0};
			static const unsigned char stress_amps_mk[8] = {16,16, 20,20, 20,24, 24,22 };
			static const short stress_lengths_mk[8] = {180,160, 200,200, 0,0, 220,230};

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_mk,stress_amps_mk);
			tr->charset_a0 = charsets[5];   // ISO-8859-5
			tr->letter_groups[0] = vowels_cyrillic;

			tr->langopts.stress_rule = 4;   // antipenultimate
			tr->langopts.numbers = 0x0429 + 0x4000;
			tr->langopts.numbers2 = 0x8a;  // variant numbers before thousands,milliards
		}
		break;


	case L('n','l'):  // Dutch
		{
			static const short stress_lengths_nl[8] = {160,135, 210,210,  0, 0, 260,280};
			tr = new Translator();

			tr->langopts.stress_rule = 0;
			tr->langopts.vowel_pause = 1;
			tr->langopts.param[LOPT_DIERESES] = 1;
			tr->langopts.param[LOPT_PREFIXES] = 1;
			SetLetterVowel(tr,'y');
		
			tr->langopts.numbers = 0x11c19;
			memcpy(tr->stress_lengths,stress_lengths_nl,sizeof(tr->stress_lengths));
		}
		break;

	case L('n','o'):  // Norwegian
		{
			static const unsigned char stress_amps_no[] = {16,16, 20,20, 20,24, 24,22 };
			static const short stress_lengths_no[8] = {160,140, 200,200, 0,0, 220,210};
			tr = new Translator();
			SetupTranslator(tr,stress_lengths_no,stress_amps_no);

			tr->langopts.stress_rule = 0;
			SetLetterVowel(tr,'y');
			tr->langopts.numbers = 0x11849;
		}
		break;

	case L('p','l'):   // Polish
		{
			static const short stress_lengths_pl[8] = {160, 190,  175, 175,  0, 0,  200, 210};
			static const unsigned char stress_amps_pl[8] = {17,13, 19,19, 20,24, 24,22 };    // 'diminished' is used to mark a quieter, final unstressed syllable

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_pl,stress_amps_pl);

			tr->charset_a0 = charsets[2];   // ISO-8859-2
			tr->langopts.stress_rule = 2;
			tr->langopts.stress_flags = 0x6;  // mark unstressed final syllables as diminished
			tr->langopts.param[LOPT_REGRESSIVE_VOICING] = 0x8;
 			tr->langopts.max_initial_consonants = 7; // for example: wchrzczony :)
 			tr->langopts.numbers=0x1009 + 0x4000;
			tr->langopts.numbers2=0x40;
			tr->langopts.param[LOPT_COMBINE_WORDS] = 4 + 0x100;  // combine 'nie' (marked with $alt2) with some 1-syllable (and 2-syllable) words (marked with $alt)
			SetLetterVowel(tr,'y');
		}
		break;

	case L('p','t'):  // Portuguese
		{
			static const short stress_lengths_pt[8] = {180, 125,  210, 210,  0, 0,  270, 295};
			static const unsigned char stress_amps_pt[8] = {16,13, 19,19, 20,24, 24,22 };    // 'diminished' is used to mark a quieter, final unstressed syllable
			tr = new Translator();
			SetupTranslator(tr,stress_lengths_pt,stress_amps_pt);
			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable

			tr->langopts.stress_rule = 3;        // stress on final syllable
			tr->langopts.stress_flags =  0x6 | 0x10 | 0x20000; 
			tr->langopts.numbers = 0x269 + 0x4000 + NUM_ROMAN;
			SetLetterVowel(tr,'y');
			ResetLetterBits(tr,0x2);
			SetLetterBits(tr,1,"bcdfgjkmnpqstvxz");      // B  hard consonants, excluding h,l,r,w,y
		}
		break;

	case L('r','o'):  // Romanian
		{
			static const short stress_lengths_ro[8] = {170, 170,  180, 180,  0, 0,  240, 260};
			static const unsigned char stress_amps_ro[8] = {15,13, 18,18, 20,22, 22,22 };

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_ro,stress_amps_ro);

			tr->langopts.stress_rule = 2;
			tr->langopts.stress_flags = 0x100 + 0x6;

			tr->charset_a0 = charsets[2];   // ISO-8859-2
			tr->langopts.numbers = 0x1029+0x6000 + NUM_ROMAN;
			tr->langopts.numbers2 = 0x1e;  // variant numbers before all thousandplex
		}
		break;

	case L('r','u'):  // Russian
			tr = new Translator_Russian();
		break;

	case L('r','w'):   // Kiryarwanda
		{
			tr = new Translator();
			tr->langopts.stress_rule = 2;
			tr->langopts.stress_flags = 0x16;
			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable

			tr->langopts.numbers = 0x61 + 0x100000 + 0x4000;
			tr->langopts.numbers2 = 0x200;  // say "thousands" before its number
		}
		break;

	case L('s','k'):   // Slovak
	case L('c','s'):   // Czech
		{
			static const unsigned char stress_amps_sk[8] = {16,16, 20,20, 20,24, 24,22 };
			static const short stress_lengths_sk[8] = {190,190, 210,210, 0,0, 210,210};
			static const char *sk_voiced = "bdgjlmnrvwzaeiouy";

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_sk,stress_amps_sk);
			tr->charset_a0 = charsets[2];   // ISO-8859-2

			tr->langopts.stress_rule = 0;
			tr->langopts.stress_flags = 0x16;  
			tr->langopts.param[LOPT_REGRESSIVE_VOICING] = 0x3;
 			tr->langopts.max_initial_consonants = 5;
			tr->langopts.spelling_stress = 1;
			tr->langopts.param[LOPT_COMBINE_WORDS] = 4;  // combine some prepositions with the following word

			tr->langopts.numbers = 0x0401 + 0x4000 + NUM_ROMAN;
			tr->langopts.numbers2 = 0x40;
			tr->langopts.thousands_sep = 0;   //no thousands separator
			tr->langopts.decimal_sep = ',';

			if(name2 == L('c','s'))
			{
				tr->langopts.numbers2 = 0x48;  // variant numbers before milliards
			}

			SetLetterVowel(tr,'y');
			SetLetterVowel(tr,'r');
			ResetLetterBits(tr,0x20);
			SetLetterBits(tr,5,sk_voiced);
		}
		break;

	case L('s','q'):  // Albanian
			tr = new Translator();
			SetLetterVowel(tr,'y');
		break;


	case L('s','v'):  // Swedish
		{
			static const unsigned char stress_amps_sv[] = {16,16, 20,20, 20,24, 24,22 };
			static const short stress_lengths_sv[8] = {160,135, 220,220, 0,0, 250,280};
			tr = new Translator();
			SetupTranslator(tr,stress_lengths_sv,stress_amps_sv);

			tr->langopts.stress_rule = 0;
			SetLetterVowel(tr,'y');
//			SetLetterBits(tr,6,"eiyäö");   // soft vowels  NOTE accented letters don't work in SetLetterBits
			tr->langopts.numbers = 0x1909;
			tr->langopts.accents = 1;
		}
		break;

	case L('s','w'):  // Swahili
		{
			static const short stress_lengths_sw[8] = {160, 170,  200, 200,    0,   0,  320, 340};
			static const unsigned char stress_amps_sw[] = {16,12, 19,19, 20,24, 24,22 };
		
			tr = new Translator();
			SetupTranslator(tr,stress_lengths_sw,stress_amps_sw);
			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable

			tr->langopts.vowel_pause = 1;
			tr->langopts.stress_rule = 2;
			tr->langopts.stress_flags =  0x6 | 0x10; 

			tr->langopts.numbers = 0x4e1;
			tr->langopts.numbers2 = 0x100;
		}
		break;

	case L('t','a'):  // Tamil
		{
			static const short stress_lengths_ta[8] = {200, 200,  210, 210,  0, 0,  230, 230};
			static const unsigned char stress_amps_ta[8] = {18,18, 18,18, 20,20, 22,22 };

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_ta,stress_amps_ta);
			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable

			tr->langopts.stress_rule = 0;
			tr->langopts.stress_flags =  0x10004;   // use 'diminished' for unstressed final syllable
			tr->letter_bits_offset = OFFSET_TAMIL;
			tr->langopts.param[LOPT_WORD_MERGE] = 1;   // don't break vowels betwen words

			memset(tr->letter_bits,0,sizeof(tr->letter_bits));
			SetLetterBitsRange(tr,LETTERGP_A,0x05,0x14);   // vowel letters
			SetLetterBitsRange(tr,LETTERGP_A,0x3e,0x4c);   // vowel signs
			SetLetterBitsRange(tr,LETTERGP_B,0x3e,0x4d);   // vowel signs, and virama
			SetLetterBitsRange(tr,LETTERGP_C,0x15,0x39);   // the main consonant range
			tr->langopts.param[LOPT_UNPRONOUNCABLE] = 1;   // disable check for unpronouncable words
		}
		break;


	case L('t','h'):  // Thai
		{
			static const short stress_lengths_th[8] = {230,150, 230,230, 230,0, 230,250};
			static const unsigned char stress_amps_th[] = {22,16, 22,22, 22,22, 22,22 };

			tr = new Translator;
			SetupTranslator(tr,stress_lengths_th,stress_amps_th);

			tr->langopts.stress_rule = 0;   // stress on final syllable of a "word"
			tr->langopts.stress_flags = 1;          // don't automatically set diminished stress (may be set in the intonation module)
			tr->langopts.tone_language = 1;   // Tone language, use  CalcPitches_Tone() rather than CalcPitches()
			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable
//			tr->langopts.tone_numbers = 1;   // a number after letters indicates a tone number (eg. pinyin or jyutping)
			tr->langopts.word_gap = 0x21;   // length of a final vowel is less dependent on the next consonant, don't merge consonant with next word
		}
		break;

	case L('t','r'):   // Turkish
		{
			static const unsigned char stress_amps_tr[8] = {18,18, 20,20, 20,24, 24,22 };
			static const short stress_lengths_tr[8] = {190,190, 190,190, 0,0, 250,270};

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_tr,stress_amps_tr);
			tr->charset_a0 = charsets[9];   // ISO-8859-9 - Latin5

			tr->langopts.stress_rule = 7;   // stress on the last syllable, before any explicitly unstressed syllable
			tr->langopts.stress_flags = 0x20;  //no automatic secondary stress

			tr->langopts.numbers = 0x1509 + 0x4000;
			tr->langopts.max_initial_consonants = 2;
		}
		break;

	case L('v','i'):  // Vietnamese
		{
			static const short stress_lengths_vi[8] = {150, 150,  180, 180,  210, 220,  220, 280};
			static const unsigned char stress_amps_vi[] = {16,16, 16,16, 24,24, 24,22 };
			static wchar_t vowels_vi[] = {
				0x61, 0xe0, 0xe1, 0x1ea3, 0xe3, 0x1ea1,			// a
				0x103, 0x1eb1, 0x1eaf, 0x1eb3, 0x1eb5, 0x1eb7,	// ă
				0xe2, 0x1ea7, 0x1ea5, 0x1ea9, 0x1eab, 0x1ead,	// â
				0x65, 0xe8, 0xe9, 0x1ebb, 0x1ebd, 0x1eb9,			// e
				0xea, 0x1ec1, 0x1ebf, 0x1ec3, 0x1ec5, 0x1ec7,	// i
				0x69, 0xec, 0xed, 0x1ec9, 0x129, 0x1ecb,			// i
				0x6f, 0xf2, 0xf3, 0x1ecf, 0xf5, 0x1ecd,			// o
				0xf4, 0x1ed3, 0x1ed1, 0x1ed5, 0x1ed7, 0x1ed9, 	// ô
				0x1a1, 0x1edd, 0x1edb, 0x1edf, 0x1ee1, 0x1ee3,	// ơ
				0x75, 0xf9, 0xfa, 0x1ee7, 0x169, 0x1ee5,			// u
				0x1b0, 0x1eeb, 0x1ee9, 0x1eed, 0x1eef, 0x1ef1,	// ư
				0x79, 0x1ef3, 0xfd, 0x1ef7, 0x1ef9, 0x1e, 0 };	// y

			tr = new Translator();
			SetupTranslator(tr,stress_lengths_vi,stress_amps_vi);
			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable

			tr->langopts.stress_rule = 0;
			tr->langopts.word_gap = 0x20;   // length of a final vowel is less dependent on the next consonant
			tr->langopts.vowel_pause = 4;
			tr->letter_groups[0] = vowels_vi;
//			tr->langopts.param[LOPT_UNPRONOUNCABLE] = 1;   // disable check for unpronouncable words
			tr->langopts.tone_language = 1;   // Tone language, use  CalcPitches_Tone() rather than CalcPitches()
			tr->langopts.unstressed_wd1 = 2;

		}
		break;

	case L('z','h'):
	case L_zhy:
		{
			static const short stress_lengths_zh[8] = {230,150, 230,230, 230,0, 240,250};  // 1=tone5. end-of-sentence, 6=tone 1&4, 7=tone 2&3
			static const unsigned char stress_amps_zh[] = {22,16, 22,22, 22,22, 22,22 };

			tr = new Translator;
			SetupTranslator(tr,stress_lengths_zh,stress_amps_zh);

			tr->langopts.stress_rule = 3;   // stress on final syllable of a "word"
			tr->langopts.stress_flags = 1;          // don't automatically set diminished stress (may be set in the intonation module)
			tr->langopts.vowel_pause = 0;
			tr->langopts.tone_language = 1;   // Tone language, use  CalcPitches_Tone() rather than CalcPitches()
			tr->langopts.length_mods0 = tr->langopts.length_mods;  // don't lengthen vowels in the last syllable
			tr->langopts.tone_numbers = 1;   // a number after letters indicates a tone number (eg. pinyin or jyutping)
			tr->langopts.ideographs = 1;
			tr->langopts.word_gap = 0x21;   // length of a final vowel is less dependent on the next consonant, don't merge consonant with next word
			if(name2 == L('z','h'))
			{
				tr->langopts.textmode = 1;
				tr->langopts.listx = 1;    // compile zh_listx after zh_list
			}
		}
		break;

	default:
		tr = new Translator();
		break;
	}

	tr->translator_name = name2;

	if(tr->langopts.numbers & 0x8)
	{
		// use . and ; for thousands and decimal separators
		tr->langopts.thousands_sep = '.';
		tr->langopts.decimal_sep = ',';
	}
	if(tr->langopts.numbers & 0x4)
	{
		tr->langopts.thousands_sep = 0;   // don't allow thousands separator, except space
	}
	return(tr);
}  // end of SelectTranslator



//**********************************************************************************************************



Translator_Russian::Translator_Russian() : Translator()
{//===================================
	static const unsigned char stress_amps_ru[] = {16,16, 18,18, 20,24, 24,22 };
	static const short stress_lengths_ru[8] = {150,140, 220,220, 0,0, 260,280};


	// character codes offset by 0x420
	static const char ru_vowels[] = {0x10,0x15,0x31,0x18,0x1e,0x23,0x2b,0x2d,0x2e,0x2f,0};
	static const char ru_consonants[] = {0x11,0x12,0x13,0x14,0x16,0x17,0x19,0x1a,0x1b,0x1c,0x1d,0x1f,0x20,0x21,0x22,0x24,0x25,0x26,0x27,0x28,0x29,0x2a,0x2c,0};
	static const char ru_soft[] = {0x2c,0x19,0x27,0x29,0};   // letter group B  [k ts; s;]
	static const char ru_hard[] = {0x2a,0x16,0x26,0x28,0};   // letter group H  [S Z ts]
	static const char ru_nothard[] = {0x11,0x12,0x13,0x14,0x17,0x19,0x1a,0x1b,0x1c,0x1d,0x1f,0x20,0x21,0x22,0x24,0x25,0x27,0x29,0x2c,0};
	static const char ru_voiced[] = {0x11,0x12,0x13,0x14,0x16,0x17,0};    // letter group G  (voiced obstruents)
	static const char ru_ivowels[] = {0x2c,0x15,0x31,0x18,0x2e,0x2f,0};   // letter group Y  (iotated vowels & soft-sign)

	SetupTranslator(this,stress_lengths_ru,stress_amps_ru);

	charset_a0 = charsets[18];   // KOI8-R
	transpose_offset = 0x42f;  // convert cyrillic from unicode into range 0x01 to 0x22
	transpose_min = 0x430;
	transpose_max = 0x451;

	letter_bits_offset = OFFSET_CYRILLIC;
	memset(letter_bits,0,sizeof(letter_bits));
	SetLetterBits(this,0,ru_vowels);
	SetLetterBits(this,1,ru_soft);
	SetLetterBits(this,2,ru_consonants);
	SetLetterBits(this,3,ru_hard);
	SetLetterBits(this,4,ru_nothard);
	SetLetterBits(this,5,ru_voiced);
	SetLetterBits(this,6,ru_ivowels);
	SetLetterBits(this,7,ru_vowels);

	langopts.param[LOPT_UNPRONOUNCABLE] = 0x432;    // [v]  don't count this character at start of word
	langopts.param[LOPT_REGRESSIVE_VOICING] = 1;
	langopts.param[LOPT_REDUCE] = 2;
	langopts.stress_rule = 5;
	langopts.stress_flags = 0x0020;  // waas 0x1010

	langopts.numbers = 0x0409;
	langopts.numbers2 = 0xc2;  // variant numbers before thousands
	langopts.phoneme_change = 1;
	langopts.testing = 2;

}  // end of Translator_Russian


#define PH(c1,c2)  (c2<<8)+c1          // combine two characters into an integer for phoneme name 
#define PY(c1,c2,c3) (c3<<16)+(c2<<8)+c1

/*
typedef struct {
	int flags;
	unsigned char stress;          // stress level of this vowel
	unsigned char stress_highest;  // the highest stress level of a vowel in this word
	unsigned char n_vowels;        // number of vowels in the word
	unsigned char vowel_this;      // syllable number of this vowel (counting from 1)
	unsigned char vowel_stressed;  // syllable number of the highest stressed vowel
} CHANGEPH;
*/


#define RUSSIAN2
#ifdef RUSSIAN2

int Translator_Russian::ChangePhonemes(PHONEME_LIST2 *phlist, int n_ph, int index, PHONEME_TAB *ph, CHANGEPH *ch)
{//==============================================================================================================
// Called for each phoneme in the phoneme list, to allow a language to make changes
// ph     The current phoneme

	int variant;
	int vowelix;
	PHONEME_TAB *prev, *next;

	if(ch->flags & 8)
		return(0);    // full phoneme translation has already been given
	// Russian vowel softening and reduction rules

	if(ph->type == phVOWEL)
	{
		int prestressed = ch->vowel_stressed==ch->vowel_this+1;  // the next vowel after this has the main stress

		#define N_VOWELS_RU   11
                static unsigned int vowels_ru[N_VOWELS_RU] = {'a','V','O','I',PH('I','#'),PH('E','#'),PH('E','2'),
PH('V','#'),PH('I','3'),PH('I','2'),PH('E','3')};


                static unsigned int vowel_replace[N_VOWELS_RU][6] = {
                        // stressed, soft, soft-stressed, j+stressed, j+soft, j+soft-stressed
                /*0*/        {'A', 'I', PH('j','a'),         'a', 'a', 'a'},                // a   Uses 3,4,5 columns.
                /*1*/        {'A', 'V', PH('j','a'),         'a', 'V', 'a'},                // V   Uses 3,4,5 columns.
                /*2*/        {'o', '8', '8',                 'o', '8', '8'},                // O
                /*3*/        {'i', 'I', 'i',                 'a', 'I', 'a'},                // I  Uses 3,4,5 columns.
                /*4*/        {'i', PH('I','#'), 'i',         'i', PH('I','#'), 'i'},        // I#
                /*5*/        {'E', PH('E','#'), 'E',         'e', PH('E','#'), 'e'},        // E# 
                /*6*/        {'E', PH('E','2'), 'E',         'e', PH('E','2'), 'e'},        // E2  Uses 3,4,5 columns.
                /*7*/        {PH('j','a'), 'V', PH('j','a'), 'A', 'V', 'A'},                // V#
                /*8*/        {PH('j','a'), 'I', PH('j','a'), 'e', 'I', 'e'},                // I3 Uses 3,4,5 columns.
                /*9*/        {'e', 'I', 'e',                 'e', 'I', 'e'},                // I2
                /*10*/       {'e', PH('E', '2'), 'e',        'e', PH('E','2'), 'e'}         // E3
                };

		prev = phoneme_tab[phlist[index-1].phcode];
		next = phoneme_tab[phlist[index+1].phcode];

		// lookup the vowel name to get an index into the vowel_replace[] table
		for(vowelix=0; vowelix<N_VOWELS_RU; vowelix++)
		{
			if(vowels_ru[vowelix] == ph->mnemonic)
				break;
		}
		if(vowelix == N_VOWELS_RU)
			return(0);

		if(prestressed)
		{
			if((vowelix==6)&&(prev->mnemonic=='j'))
				vowelix=8;
			if(vowelix==1)
				vowelix=0;
			if(vowelix==4)
				vowelix=3;
			if(vowelix==6)
				vowelix=5;
			if(vowelix==7)
				vowelix=8;
			if(vowelix==10)
				vowelix=9;
		}
		// do we need a variant of this vowel, depending on the stress and adjacent phonemes ?
		variant = -1;
		int stressed = ch->flags & 2;
		int soft=prev->phflags & phPALATAL;

		if (soft && stressed)
			variant = 2; else
				if (stressed)
					variant = 0; else
						if (soft)
							variant = 1;
		if(variant >= 0)
		{
			if(prev->mnemonic == 'j')
				variant += 3;

			phlist[index].phcode = PhonemeCode(vowel_replace[vowelix][variant]);
		}
		else
		{
			phlist[index].phcode = PhonemeCode(vowels_ru[vowelix]);
		}
	}

	return(0);
}
#else


int Translator_Russian::ChangePhonemes(PHONEME_LIST2 *phlist, int n_ph, int index, PHONEME_TAB *ph, CHANGEPH *ch)
{//===============================================================================================================
// Called for each phoneme in the phoneme list, to allow a language to make changes
// flags: bit 0=1 last phoneme in a word
//        bit 1=1 this is the highest stressed vowel in the current word
//        bit 2=1 after the highest stressed vowel in the current word
//        bit 3=1 the phonemes were specified explicitly, or found from an entry in the xx_list dictionary
// ph     The current phoneme

	int variant;
	int vowelix;
	PHONEME_TAB *prev, *next;

	if(ch->flags & 8)
		return(0);    // full phoneme translation has already been given

	// Russian vowel softening and reduction rules
	if(ph->type == phVOWEL)
	{
		#define N_VOWELS_RU   7
		static unsigned char vowels_ru[N_VOWELS_RU] = {'a','A','o','E','i','u','y'};

		// each line gives: soft, reduced, soft-reduced, post-tonic
		static unsigned short vowel_replace[N_VOWELS_RU][4] = {
			{'&', 'V', 'I', 'V'},  // a
			{'&', 'V', 'I', 'V'},  // A
			{'8', 'V', 'I', 'V'},  // o
			{'e', 'I', 'I', 'I'},  // E
			{'i', 'I', 'I', 'I'},  // i
			{'u'+('"'<<8), 'U', 'U', 'U'},  // u
			{'y', 'Y', 'Y', 'Y'}};  // y

		prev = phoneme_tab[phlist[index-1].phcode];
		next = phoneme_tab[phlist[index+1].phcode];

if(prev->mnemonic == 'j')
  return(0);

		// lookup the vowel name to get an index into the vowel_replace[] table
		for(vowelix=0; vowelix<N_VOWELS_RU; vowelix++)
		{
			if(vowels_ru[vowelix] == ph->mnemonic)
				break;
		}
		if(vowelix == N_VOWELS_RU)
			return(0);

		// do we need a variant of this vowel, depending on the stress and adjacent phonemes ?
		variant = -1;
		if(ch->flags & 2)
		{
			// a stressed vowel
			if((prev->phflags & phPALATAL) && ((next->phflags & phPALATAL) || phoneme_tab[phlist[index+2].phcode]->mnemonic == ';'))
			{
				// between two palatal consonants, use the soft variant
				variant = 0;
			}
		}
		else
		{
			// an unstressed vowel
			if(prev->phflags & phPALATAL)
			{
				variant = 2;  // unstressed soft
			}
			else
			if((ph->mnemonic == 'o') && ((prev->phflags & phPLACE) == phPLACE_pla))
			{
				variant = 2;  // unstressed soft  ([o] vowel following:  ш ж
			}
			else
			if(ch->flags & 4)
			{
				variant = 3;  // post tonic
			}
			else
			{
				variant = 1;  // unstressed
			}
		}
		if(variant >= 0)
		{
			phlist[index].phcode = PhonemeCode(vowel_replace[vowelix][variant]);
		}
	}

	return(0);
}
#endif

