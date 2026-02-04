package com.kashif.folar.enums

/**
 * Enum representing aspect ratios for camera preview and capture.
 * 
 * - RATIO_4_3: Standard 4:3 aspect ratio (default on most devices)
 * - RATIO_16_9: Widescreen 16:9 aspect ratio  
 * - RATIO_9_16: Portrait 9:16 aspect ratio (useful for stories, full-screen vertical content)
 * - RATIO_1_1: Square 1:1 aspect ratio
 */
enum class AspectRatio {
    RATIO_4_3,  // Standard Landscape 4:3
    RATIO_3_4,  // Standard Portrait 3:4
    RATIO_16_9, // Standard Landscape 16:9
    RATIO_9_16, // Standard Portrait 9:16 (Stories)
    RATIO_1_1,  // Square 1:1
    RATIO_4_5   // Instagram Portrait 4:5
}
