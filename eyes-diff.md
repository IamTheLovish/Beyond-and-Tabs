# Eyes picture regression (tools/eyes_diff.py)

Compared with the last approved stills. Flagged = more than 25% of the frame changed, a solid region changed, or the overall brightness moved more than 0.10 - look at eyes-diff.jpg (approved | new | changed cells).

- compared: 37, flagged: 27, missing: 0, new: 26

**New (not in the approved set):** bat_eyes_01b_start_boxes_z36.png, bat_eyes_01c_start_boxes_z60.png, bat_eyes_01d_start_boxes_z100.png, bat_eyes_01e_start_placed_z36.png, bat_eyes_01f_start_boxes_ground.png, bat_eyes_05m_forest_rts.png, bat_eyes_05n_forest_rts_close.png, bat_eyes_07t_bone_dragon_close.png, bat_eyes_07u_bone_dragon_fly.png, bat_eyes_07v_bone_dragon_bite.png, bat_eyes_07w_mossback_bison_close.png, bat_eyes_07x_mossback_bison_walk.png, bat_eyes_07y_mossback_bison_attack.png, bat_eyes_07z_mesh_ab_1_cube.png, bat_eyes_07z_mesh_ab_1_mesh.png, bat_eyes_11b_capitol_selected.png, bat_eyes_16_scars_before_rts.png, bat_eyes_16_scars_fp_crater.png, bat_eyes_16_scars_fp_steps.png, bat_eyes_16_scars_rts.png, bat_eyes_16_scars_rts_close.png, bat_eyes_16_scars_rts_crater.png, bat_eyes_16_scars_rts_far.png, bat_eyes_17_fog_edge.png, bat_eyes_17_fog_edge_remeshed.png, bat_eyes_17_fog_trail.png

| still | changed | solid region | brightness | flag |
|---|---|---|---|---|
| 00_unexpected_screen | 0% |  | +0.00 |  |
| 01_title | 49% | yes | -0.05 | **yes** |
| 02_rts_default | 47% | yes | -0.02 | **yes** |
| 02b_rts_default_look_off | 43% | yes | -0.01 | **yes** |
| 03_rts_close | 76% | yes | +0.15 | **yes** |
| 04_rts_far | 49% | yes | -0.10 | **yes** |
| 05_rts_one_minute | 54% | yes | -0.09 | **yes** |
| 06_build_menu | 62% | yes | -0.04 | **yes** |
| 07_army_close | 35% | yes | -0.07 | **yes** |
| 07b_paladin_close | 27% |  | -0.03 | **yes** |
| 07c_paladin_walk | 17% |  | -0.06 |  |
| 07d_paladin_attack | 31% |  | -0.06 | **yes** |
| 07e_medic_close | 29% | yes | -0.02 | **yes** |
| 07f_medic_walk | 13% |  | -0.05 |  |
| 07g_medic_throw | 32% | yes | -0.06 | **yes** |
| 07h_halberdier_close | 53% | yes | +0.05 | **yes** |
| 07i_halberdier_walk | 27% |  | -0.02 | **yes** |
| 07j_halberdier_attack | 33% | yes | -0.05 | **yes** |
| 07k_levy_close | 18% |  | -0.02 |  |
| 07l_levy_walk | 12% |  | -0.05 |  |
| 07m_levy_shoot | 13% |  | -0.03 |  |
| 07n_squire_close | 27% | yes | -0.03 | **yes** |
| 07o_squire_run | 9% |  | -0.05 |  |
| 07p_squire_attack | 20% | yes | -0.04 | **yes** |
| 07q_squire_bow_close | 21% |  | -0.01 |  |
| 07r_squire_bow_run | 10% |  | -0.04 |  |
| 07s_squire_shoot | 25% |  | -0.04 |  |
| 08_box_drag | 39% | yes | -0.06 | **yes** |
| 08_box_select | 47% | yes | -0.04 | **yes** |
| 09_fight | 57% | yes | -0.01 | **yes** |
| 10_fight_strategic | 58% | yes | -0.11 | **yes** |
| 11_capitol_two_minutes | 47% | yes | -0.00 | **yes** |
| 12_river_crossing | 42% | yes | -0.10 | **yes** |
| 12_river_crossing_6s | 35% |  | -0.09 | **yes** |
| 13_dome | 48% | yes | -0.09 | **yes** |
| 13_dome_hit | 47% | yes | -0.08 | **yes** |
| 14_first_person | 72% | yes | +0.06 | **yes** |
