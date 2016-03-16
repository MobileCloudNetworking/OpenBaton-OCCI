#!/usr/bin/env bash

source ./gradle.properties

#occi_api_base="/opt/occi-api"
occi_api_base=${PWD}
occi_api_config="/etc/occi-api/occi-api.properties"

check_already_running() {
        result=$(screen -ls | grep occi-api | wc -l);
        if [ "${result}" -ne "0" ]; then
                echo "OCCI-API is already running.."
        exit;
        fi
}

start() {
    screen -d -m -S occi-api -t occi-api java -jar ${occi_api_base}"/build/libs/occi-api-$version.jar" --spring.config.location=file:${occi_api_config}
}

stop() {
    if screen -list | grep "occi-api"; then
        screen -S occi-api -p 0 -X stuff $'\003'
    fi
}

restart() {
    stop
    start
}


force-stop() {
    if screen -list | grep "occi-api"; then
        screen -ls | grep "occi-api" | cut -d. -f1 | awk '{print $1}' | xargs kill
    fi
}

init() {
    if [ ! -f $occi_api_config ]; then
        if [ $EUID != 0 ]; then
            echo "creating the directory and copying the file"
            sudo -E sh -c "mkdir -p /etc/occi-api; cp ${occi_api_base}/src/main/resources/application.properties ${occi_api_config}"
        else
            echo "creating the directory"
            mkdir /etc/occi-api
            echo "copying the file"
            cp ${occi_api_base}/src/main/resources/paas.properties ${occi_api_config}
        fi
    else
        echo "Properties file already exist"
    fi
}

compile() {
    ./gradlew build -x test
}

tests() {
    ./gradlew test
}

clean() {
    ./gradlew clean
}

end() {
    exit
}

usage() {
    echo "usage: ./occi-api.sh [compile|start|stop|test|kill|clean]"
}

if [ $# -eq 0 ]
   then
        usage
        exit 1
fi

declare -a cmds=($@)
for (( i = 0; i <  ${#cmds[*]}; ++ i ))
do
    case ${cmds[$i]} in
        "clean" )
            clean ;;
        "sc" )
            clean
            compile
            start ;;
        "start" )
            start ;;
        "stop" )
            stop ;;
        "init" )
            init ;;
        "restart" )
            restart ;;
        "compile" )
            compile ;;
        "kill" )
            force-stop ;;
        "test" )
            tests ;;
        * )
            usage
            end ;;
    esac
    if [[ $? -ne 0 ]];
    then
        exit 1
    fi
done