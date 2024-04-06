#!/bin/bash

start () {
  docker compose up -d
}

stop () {
  docker compose down
}

init () {
  echo "Initializing"
  source ./.env
  mkdir -p "${NEO4J_DATA_DIR}"
  docker compose --verbose pull
}

usage () {
	echo "Starts/stops the File Locator application and Neo4J."
	echo -e "Usage: $0 [option]"
	echo -e 'Options:'
	echo -e '  --start:         Starts the app and Neo4J'
	echo -e '  --stop:          Stops the app and Neo4J'
}

process_action () {
  TEMP=$(getopt -o hsp --long help,start,stop -- "$1")
  eval set -- "$TEMP"
  local action='--help'
  case "$1" in
    -s|--start)
      action='init && start'
      ;;
    -p|--stop)
      action='stop'
      ;;
    -h|--help)
      ;&
    *)
      action='usage && exit 1'
      ;;
  esac
  echo "${action}"
}

main () {
  start_dir=$(pwd)
  pushd "$(dirname "$0")" > /dev/null || echo "Could not switch to program dir"
  action=$(process_action "$1")
  eval "${action}"
  popd > /dev/null || echo "Could not return to original dir: ${start_dir}"
}

main "$@"