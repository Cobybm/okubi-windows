cmake_minimum_required(VERSION 3.22.1)
project(okubi_native C)

add_library(okubi_sameboy_bridge SHARED sameboy_bridge.c)
target_compile_features(okubi_sameboy_bridge PRIVATE c_std_11)
target_include_directories(okubi_sameboy_bridge PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(okubi_sameboy_bridge PRIVATE -O2 -Wall -Wextra -Werror=format-security)
target_link_libraries(okubi_sameboy_bridge PRIVATE dl log)
