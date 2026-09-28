# Robot command inventory — python-roborock-main

Repository scanned: `roborock/` source tree (tests excluded for usage counts).

## 1. V1 raw command catalog (253 commands)

These are string RPC commands accepted by `device.v1_properties.command.send(command, params)`. Availability and parameter formats depend on robot model and firmware.

### App / robot actions (50)

`app_amethyst_self_check` · `app_charge` · `app_delete_wifi` · `app_get_amethyst_status` · `app_get_carpet_deep_clean_status` · `app_get_clean_estimate_info`

`app_get_dryer_setting` · `app_get_init_status` · `app_get_locale` · `app_get_wifi_list` · `app_goto_target` · `app_keep_easter_egg`

`app_pause` · `app_rc_end` · `app_rc_move` · `app_rc_start` · `app_rc_stop` · `app_resume_build_map`

`app_resume_patrol` · `app_segment_clean` · `app_set_amethyst_status` · `app_set_carpet_deep_clean_status` · `app_set_cross_carpet_cleaning_status` · `app_set_door_sill_blocks`

`app_set_dirty_replenish_clean_status` · `app_set_dryer_setting` · `app_set_dryer_status` · `app_set_dynamic_config` · `app_set_ignore_stuck_point` · `app_set_smart_cliff_forbidden`

`app_set_smart_door_sill` · `app_spot` · `app_start` · `app_start_build_map` · `app_start_collect_dust` · `app_start_easter_egg`

`app_start_patrol` · `app_start_pet_patrol` · `app_start_wash` · `app_stat` · `app_stop` · `app_stop_collect_dust`

`app_stop_wash` · `app_update_unsave_map` · `app_wakeup_robot` · `app_zoned_clean` · `app_empty_rinse_tank_water` · `app_ignore_dirty_objects`

`app_set_robot_setting` · `app_get_robot_setting`

### Read / query (79)

`get_auto_delivery_cleaning_fluid` · `get_camera_status` · `get_carpet_clean_mode` · `get_carpet_mode` · `get_child_lock_status` · `get_clean_follow_ground_material_status`

`get_clean_motor_mode` · `get_clean_record` · `get_clean_record_map` · `get_clean_sequence` · `get_clean_summary` · `get_collision_avoid_status`

`get_consumable` · `get_current_sound` · `get_custom_mode` · `get_customize_clean_mode` · `get_device_ice` · `get_device_sdp`

`get_dnd_timer` · `get_dock_info` · `get_dust_collection_mode` · `get_dust_collection_switch_status` · `get_dynamic_data` · `get_dynamic_map_diff`

`get_fan_motor_work_timeout` · `get_flow_led_status` · `get_fresh_map` · `get_fw_features` · `get_homesec_connect_status` · `get_identify_furniture_status`

`get_identify_ground_material_status` · `get_led_status` · `get_log_upload_status` · `get_map` · `get_map_beautification_status` · `get_map_status`

`get_map_v1` · `get_map_v2` · `get_map_calibration` · `get_mop_motor_status` · `get_mop_template_params_by_id` · `get_mop_template_params_summary`

`get_multi_map` · `get_multi_maps_list` · `get_network_info` · `get_offline_map_status` · `get_persist_map` · `get_prop`

`get_random_pkey` · `get_recover_map` · `get_recover_maps` · `get_room_mapping` · `get_scenes_valid_tids` · `get_segment_status`

`get_serial_number` · `get_server_timer` · `get_smart_wash_params` · `get_sound_progress` · `get_sound_volume` · `get_status`

`get_testid` · `get_timer` · `get_timer_detail` · `get_timer_summary` · `get_timezone` · `get_turn_server`

`get_valley_electricity_timer` · `get_wash_debug_params` · `get_wash_towel_mode` · `get_wash_towel_params` · `get_water_box_custom_mode` · `get_stretch_tag_status`

`get_right_brush_stretch_status` · `get_dirty_object_detect_status` · `get_wash_water_temperature` · `get_pet_supplies_deep_clean_status` · `get_ap_mic_led_status` · `get_handle_leak_water_status`

`get_gap_deep_clean_status`

### Configuration (57)

`set_airdry_hours` · `set_app_timezone` · `set_auto_delivery_cleaning_fluid` · `set_camera_status` · `set_carpet_area` · `set_carpet_clean_mode`

`set_carpet_mode` · `set_child_lock_status` · `set_clean_follow_ground_material_status` · `set_clean_motor_mode` · `set_clean_sequence` · `set_clean_repeat_times`

`set_collision_avoid_status` · `set_custom_mode` · `set_customize_clean_mode` · `set_dnd_timer` · `set_dnd_timer_actions` · `set_dust_collection_mode`

`set_dust_collection_switch_status` · `set_fan_motor_work_timeout` · `set_fds_endpoint` · `set_flow_led_status` · `set_homesec_password` · `set_identify_furniture_status`

`set_identify_ground_material_status` · `set_ignore_carpet_zone` · `set_ignore_identify_area` · `set_lab_status` · `set_led_status` · `set_map_beautification_status`

`set_mop_mode` · `set_mop_motor_status` · `set_mop_template_id` · `set_offline_map_status` · `set_scenes_segments` · `set_scenes_zones`

`set_segment_ground_material` · `set_server_timer` · `set_smart_wash_params` · `set_switch_map_mode` · `set_timer` · `set_timezone`

`set_valley_electricity_timer` · `set_voice_chat_volume` · `set_wash_debug_params` · `set_wash_towel_mode` · `set_wash_towel_params` · `set_water_box_custom_mode`

`set_water_box_distance_off` · `set_stretch_tag_status` · `set_right_brush_stretch_status` · `set_dirty_object_detect_status` · `set_wash_water_temperature` · `set_pet_supplies_deep_clean_status`

`set_ap_mic_led_status` · `set_handle_leak_water_status` · `set_gap_deep_clean_status`

### Start actions (5)

`start_camera_preview` · `start_clean` · `start_edit_map` · `start_voice_chat` · `start_wash_then_charge`

### Stop actions (6)

`stop_camera_preview` · `stop_fan_motor_work` · `stop_goto_target` · `stop_segment_clean` · `stop_voice_chat` · `stop_zoned_clean`

### Delete (6)

`del_clean_record` · `del_clean_record_map_v2` · `del_map` · `del_mop_template_params` · `del_server_timer` · `del_timer`

### Other / map / service (50)

`add_mop_template_params` · `change_sound_volume` · `check_homesec_password` · `close_dnd_timer` · `close_valley_electricity_timer` · `dnld_install_sound`

`enable_homesec_voice` · `enable_log_upload` · `end_edit_map` · `find_me` · `load_multi_map` · `manual_bak_map`

`manual_segment_map` · `merge_segment` · `mop_mode` · `mop_template_id` · `name_multi_map` · `name_segment`

`play_audio` · `recover_map` · `recover_multi_map` · `reset_consumable` · `reset_homesec_password` · `reset_map`

`resolve_error` · `resume_segment_clean` · `resume_zoned_clean` · `retry_request` · `reunion_scenes` · `save_furnitures`

`save_map` · `send_ice_to_robot` · `send_sdp_to_robot` · `sort_mop_template_params` · `split_segment` · `switch_video_quality`

`switch_water_mark` · `test_sound_volume` · `upd_server_timer` · `upd_timer` · `update_dock` · `update_mop_template_params`

`upload_data_for_debug_mode` · `upload_photo` · `use_new_map` · `use_old_map` · `user_upload_log` · `matter.get_status`

`matter.dnld_key` · `matter.reset`

## 2. V1 commands explicitly wired into source traits (32)

- `app_get_init_status` — `roborock/cli.py:1172`, `roborock/devices/traits/v1/device_features.py:34`
- `app_start_wash` — `roborock/devices/traits/v1/wash_towel_mode.py:45`
- `app_stop_wash` — `roborock/devices/traits/v1/wash_towel_mode.py:49`
- `change_sound_volume` — `roborock/devices/traits/v1/volume.py:24`
- `close_dnd_timer` — `roborock/devices/traits/v1/do_not_disturb.py:26`, `roborock/devices/traits/v1/do_not_disturb.py:40`
- `close_valley_electricity_timer` — `roborock/devices/traits/v1/valley_electricity_timer.py:27`, `roborock/devices/traits/v1/valley_electricity_timer.py:42`
- `get_child_lock_status` — `roborock/devices/traits/v1/child_lock.py:11`
- `get_clean_record` — `roborock/devices/traits/v1/clean_summary.py:90`, `roborock/devices/traits/v1/clean_summary.py:96`
- `get_clean_summary` — `roborock/devices/traits/v1/clean_summary.py:71`
- `get_consumable` — `roborock/devices/traits/v1/consumeable.py:52`
- `get_dnd_timer` — `roborock/devices/traits/v1/do_not_disturb.py:11`
- `get_dust_collection_mode` — `roborock/devices/traits/v1/dust_collection_mode.py:12`
- `get_flow_led_status` — `roborock/devices/traits/v1/flow_led_status.py:11`
- `get_led_status` — `roborock/devices/traits/v1/led_status.py:28`
- `get_map_v1` — `roborock/devices/traits/v1/home.py:42`, `roborock/devices/traits/v1/map_content.py:86`
- `get_multi_maps_list` — `roborock/devices/traits/v1/maps.py:53`
- `get_network_info` — `roborock/devices/rpc/v1_channel.py:390`, `roborock/devices/traits/v1/network_info.py:33`
- `get_room_mapping` — `roborock/devices/traits/v1/rooms.py:84`
- `get_smart_wash_params` — `roborock/devices/traits/v1/smart_wash_params.py:12`
- `get_sound_volume` — `roborock/devices/traits/v1/volume.py:19`
- `get_status` — `roborock/devices/traits/v1/status.py:52`
- `get_valley_electricity_timer` — `roborock/devices/traits/v1/valley_electricity_timer.py:11`
- `get_wash_towel_mode` — `roborock/devices/traits/v1/wash_towel_mode.py:16`
- `load_multi_map` — `roborock/devices/traits/v1/maps.py:82`
- `reset_consumable` — `roborock/devices/traits/v1/consumeable.py:62`
- `set_child_lock_status` — `roborock/devices/traits/v1/child_lock.py:22`, `roborock/devices/traits/v1/child_lock.py:28`
- `set_dnd_timer` — `roborock/devices/traits/v1/do_not_disturb.py:21`, `roborock/devices/traits/v1/do_not_disturb.py:32`
- `set_flow_led_status` — `roborock/devices/traits/v1/flow_led_status.py:22`, `roborock/devices/traits/v1/flow_led_status.py:28`
- `set_led_status` — `roborock/devices/traits/v1/led_status.py:39`, `roborock/devices/traits/v1/led_status.py:45`
- `set_valley_electricity_timer` — `roborock/devices/traits/v1/valley_electricity_timer.py:22`, `roborock/devices/traits/v1/valley_electricity_timer.py:33`
- `set_wash_towel_mode` — `roborock/devices/traits/v1/wash_towel_mode.py:41`

## 3. B01/Q7 named method catalog

60 known Q7 method strings are declared. The user-facing action wrappers include `service.set_room_clean`, `service.start_recharge`, and `service.find_device`; details are in `roborock/devices/traits/b01/q7/__init__.py`.

`add_clean_failed.post` · `event.add_clean_failed.post` · `clean_finish.post` · `event.clean_finish.post` · `event.BuildMapFinish.post` · `event.map_change.post`

`event.work_appoint_clean_failed.post` · `startClean.post` · `service.add_order` · `service.add_sweep_clean` · `service.arrange_room` · `service.del_map`

`service.del_order` · `service.del_orders` · `service.delete_record_by_url` · `service.download_voice_type` · `service.erase_preference` · `service.find_device`

`service.get_room_order` · `service.get_voice_download` · `service.hello_wikka` · `service.rename_map` · `service.rename_room` · `service.rename_rooms`

`service.replace_map` · `service.reset_consumable` · `service.save_carpet` · `service.save_recommend_fb` · `service.save_sill` · `service.set_area_start`

`service.set_areas_start` · `service.set_cur_map` · `service.set_direction` · `service.set_global_sort` · `service.set_map_hide` · `service.set_multi_room_material`

`service.set_point_clean` · `service.set_preference` · `service.set_preference_type` · `service.set_quiet_time` · `service.set_room_clean` · `service.set_room_order`

`service.set_virtual_wall` · `service.set_zone_clean` · `service.set_zone_points` · `service.split_room` · `service.start_explore` · `service.start_point_clean`

`service.start_recharge` · `service.stop_recharge` · `service.upload_by_mapid` · `service.upload_record_by_url` · `prop.get` · `service.get_map_list`

`service.upload_by_maptype` · `prop.set` · `service.get_preference` · `service.get_record_list` · `service.get_order` · `prop.post`

## 4. B01/Q10 direct robot actions

- Start whole-home cleaning: DP `201`, parameter `1`
- Start room/segment cleaning: DP `201`, parameter `{"cmd": 2, "clean_paramters": [room IDs]}`
- Spot clean: DP `201`, parameter `5`
- Pause: DP `204`, parameter `0`
- Resume: DP `205`, parameter `0`
- Stop: DP `206`, parameter `0`
- Return to dock/charge: DP `202`, parameter `5`
- Empty dustbin: DP `203`, parameter `2`
- Set cleaning mode: DP `137` (enum-mapped)
- Set fan level: DP `123` (enum-mapped)
