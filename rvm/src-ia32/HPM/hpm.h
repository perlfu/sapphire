/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 *
 * $Id$
 */
#ifndef _H_HPM
#define _H_HPM
#include "papi.h"

/* 
 * Header file for hpm.c 
 * @author Peter F. Sweeney
 *
 * @modified Peter F. Sweeney 11/25/2003 to support PAPI IA_32 on linux by
 * incorporating contribution from Kien Le, Tuan Phan and Stephen Smaldone at Rutgers University.
 */

#define False	0
#define True	1
#define ERROR_CODE	  -1
#define OK_CODE		   0

#define MODE_IS_GROUP      1	/* is an event group (only event 1 should be defined) */
#define MODE_PROCESS       2	/* process level group indicator */
#define MODE_KERNEL        4	/* turns kernel mode counting on */
#define MODE_USER          8	/* turns user mode counting on */
#define MODE_COUNT        16	/* counting state */
#define MODE_PROCTREE     32	/* turns process tree counting on */
#define MODE_ALL          64	/* set on all modes */
#define MODE_UPPER_BOUND 127	/* upper bound */

int filter = 0;

extern "C" int   hpm_init(int my_filter);
extern "C" int   hpm_get_number_of_counters();
extern "C" int   hpm_get_number_of_events();
extern "C" char *hpm_get_processor_name();
extern "C" int   hpm_is_big_endian();
extern "C" int   hpm_isPower4();
extern "C" int   hpm_isPower3II();
extern "C" int   hpm_isPower3();
extern "C" int   hpm_isRS64III();
extern "C" int   hpm_is604e();

extern "C" int hpm_set_event(int e1, int e2, int e3, int e4);
extern "C" int hpm_set_event_X(int e5, int e6, int e7, int e8);
extern "C" int hpm_set_mode(int mode);

extern "C" int hpm_set_program_mythread();
extern "C" int hpm_set_program_mygroup();

extern "C" int   hpm_get_event_id(        int counter);
extern "C" char *hpm_get_event_short_name(int counter);

extern "C" int hpm_delete_program_mythread();
extern "C" int hpm_delete_program_mygroup();

extern "C" int hpm_get_program_mythread();
extern "C" int hpm_get_program_mygroup();

extern "C" int hpm_start_mythread();
extern "C" int hpm_start_mygroup();

extern "C" int hpm_stop_mythread();
extern "C" int hpm_stop_mygroup();

extern "C" int hpm_reset_mythread();
extern "C" int hpm_reset_mygroup();
extern "C" int hpm_get_mythread();
extern "C" int hpm_get_mygroup();

extern "C" long long hpm_get_counter_mythread(int);
extern "C" long long hpm_get_counter_mygroup(int);

extern "C" int hpm_print_mythread();
extern "C" int hpm_print_mygroup();
extern "C" int hpm_print_events();

extern "C" int print_events(int*);
extern "C" int hpm_test();

extern "C" void hpm_list_events();

extern "C" void hpm_list_all_events();

extern "C" void hpm_list_selected_events();

extern "C" void hpm_print_data(long long *);
extern "C" void hpm_print_header(int, int);

extern "C" int* hpm_get_group_event_list(int);
extern "C" void hpm_print_group_event_list(int);

#endif
