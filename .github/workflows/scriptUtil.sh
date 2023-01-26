# Source this file to use its functions

# Gets a property out of a .properties file
# usage: getProperty $key $filename
function getProperty() {
    grep "${1}" "$2" | cut -d'=' -f2
}
