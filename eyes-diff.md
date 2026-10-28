# Eyes picture regression (tools/eyes_diff.py)

Compared with the last approved stills. Flagged = more than 25% of the frame changed, a solid region changed, or the overall brightness moved more than 0.10 - look at eyes-diff.jpg (approved | new | changed cells).

- compared: 37, flagged: 36, missing: 0, new: 23

**New (not in the approved set):** bat_eyes_01b_start_boxes_z36.png, bat_eyes_01c_start_boxes_z60.png, bat_eyes_01d_start_boxes_z100.png, bat_eyes_01e_start_placed_z36.png, bat_eyes_01f_start_boxes_ground.png, bat_eyes_07t_bone_dragon_close.png, bat_eyes_07u_bone_dragon_fly.png, bat_eyes_07v_bone_dragon_bite.png, bat_eyes_07w_mossback_bison_close.png, bat_eyes_07x_mossback_bison_walk.png, bat_eyes_07y_mossback_bison_attack.png, bat_eyes_07z_mesh_ab_1_cube.png, bat_eyes_07z_mesh_ab_1_cube_plain.png, bat_eyes_07z_mesh_ab_1_diff.png, bat_eyes_07z_mesh_ab_1_mesh.png, bat_eyes_11b_capitol_selected.png, bat_eyes_16_scars_before_rts.png, bat_eyes_16_scars_fp_crater.png, bat_eyes_16_scars_fp_steps.png, bat_eyes_16_scars_rts.png, bat_eyes_16_scars_rts_close.png, bat_eyes_16_scars_rts_crater.png, bat_eyes_16_scars_rts_far.png

| still | changed | solid region | brightness | flag |
|---|---|---|---|---|
| 00_unexpected_screen | 0% |  | +0.00 |  |
| 01_title | 44% |  | -0.04 | **yes** |
| 02_rts_default | 63% | yes | -0.13 | **yes** |
| 02b_rts_default_look_off | 64% | yes | -0.13 | **yes** |
| 03_rts_close | 50% |  | -0.07 | **yes** |
| 04_rts_far | 44% | yes | -0.07 | **yes** |
| 05_rts_one_minute | 80% | yes | -0.22 | **yes** |
| 06_build_menu | 67% | yes | -0.14 | **yes** |
| 07_army_close | 87% | yes | -0.22 | **yes** |
| 07b_paladin_close | 69% | yes | -0.14 | **yes** |
| 07c_paladin_walk | 81% | yes | -0.17 | **yes** |
| 07d_paladin_attack | 82% | yes | -0.19 | **yes** |
| 07e_medic_close | 67% | yes | -0.12 | **yes** |
| 07f_medic_walk | 81% | yes | -0.20 | **yes** |
| 07g_medic_throw | 90% | yes | -0.22 | **yes** |
| 07h_halberdier_close | 71% | yes | -0.15 | **yes** |
| 07i_halberdier_walk | 91% | yes | -0.21 | **yes** |
| 07j_halberdier_attack | 85% | yes | -0.24 | **yes** |
| 07k_levy_close | 71% | yes | -0.14 | **yes** |
| 07l_levy_walk | 87% | yes | -0.20 | **yes** |
| 07m_levy_shoot | 88% | yes | -0.21 | **yes** |
| 07n_squire_close | 67% | yes | -0.13 | **yes** |
| 07o_squire_run | 86% | yes | -0.20 | **yes** |
| 07p_squire_attack | 83% | yes | -0.22 | **yes** |
| 07q_squire_bow_close | 62% | yes | -0.11 | **yes** |
| 07r_squire_bow_run | 82% | yes | -0.19 | **yes** |
| 07s_squire_shoot | 78% | yes | -0.20 | **yes** |
| 08_box_drag | 81% | yes | -0.12 | **yes** |
| 08_box_select | 71% | yes | -0.15 | **yes** |
| 09_fight | 64% | yes | -0.13 | **yes** |
| 10_fight_strategic | 63% | yes | -0.15 | **yes** |
| 11_capitol_two_minutes | 75% | yes | -0.17 | **yes** |
| 12_river_crossing | 89% | yes | -0.27 | **yes** |
| 12_river_crossing_6s | 89% | yes | -0.25 | **yes** |
| 13_dome | 93% | yes | -0.25 | **yes** |
| 13_dome_hit | 93% | yes | -0.24 | **yes** |
| 14_first_person | 81% | yes | -0.03 | **yes** |
