(ns aws
  (:require [amazonica.aws.securitytoken :as sts]
            [amazonica.aws.identitymanagement :as iam]
            [amazonica.aws.ecr :as ecr]
            [amazonica.aws.ecs :as ecs]
            [amazonica.aws.ec2 :as ec2]
            [amazonica.aws.logs :as cwl]
            [amazonica.aws.route53 :as r53]
            [amazonica.aws.autoscaling :as asc]
            [cheshire.core :as json]
            [com.rpl.specter :as sp]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.util UUID Base64]
           [com.amazonaws.services.identitymanagement.model NoSuchEntityException]
           [com.amazonaws.services.ec2.model DomainType]
           [com.amazonaws.services.route53.model ChangeAction RRType]))

;; # AWS Account

(def aws-account-id "655043939509")

;; To avoid accidental use of a productive account, we check here the numeber of account and
;; exit if it is not a developer account.
(defn check-dev-account!! [& skip-check]
  (when-not (System/getenv "CIRCLECI")
    (let [caller-identity (sts/get-caller-identity :version "2011-06-15")]
      (if (or (= (:account caller-identity) aws-account-id)
              skip-check)
        caller-identity
        (throw (ex-info "You are not logged in the DEV account, please change your credentials"
                        caller-identity))))))
(defonce caller-identity (check-dev-account!!))

;; # Names
;;

(def circleci-user-name "circleci-zcljug")
(def repository-name "zcljug")

(def project-name "zcljug")
(def project-prefix (str project-name "-"))

(def cloudwatch-log-group-name (str "/" project-name))

(def allow-eip-policy-name (str project-prefix "allow-eip-policy"))
(def ec2-instance-name project-name)
(def ec2-instance-role-name (str project-prefix "ec2-instance"))
(def ec2-instance-security-group-name (str project-prefix "ec2-instance-sg"))

(def ecs-cluster-name project-name)
(def ecs-service-role-name (str project-prefix "ecs-service"))

(def launch-configuration-name (str project-prefix "lc"))
(def auto-scaling-group-name (str project-prefix "asg"))

(def task-description-family-name project-name)
(def service-name project-name)
(def dns-zone-name "oscillator.ch")
(def dns-name (str project-name "." dns-zone-name))
(def container-name project-name)

(def task-role-name (str project-prefix "task"))

;; # Circle CI User

(defn create-circleci-user []
  (iam/create-user :user-name circleci-user-name))
(defn attach-circleci-user-policy []
  (iam/attach-user-policy
    :user-name circleci-user-name
    :policy-arn "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"))

;; We need the CircleCI user ARN for some operations.
(defn circleci-user-arn []
  (sp/select-one! [:user :arn]
                  (iam/get-user :user-name circleci-user-name)))

;; To configure CircleCI, we need some credentials for our CircleCI user. The function `create-circleci-access-key`
;; will create a new access/secret key pair and put it in the given file.
(defn create-circleci-access-key []
  (let [result     (iam/create-access-key
                     :user-name circleci-user-name)
        access-key (sp/select-one! [:access-key :access-key-id] result)
        secret-key (sp/select-one! [:access-key :secret-access-key] result)]
    {:access-key access-key
     :secret-key secret-key}))

;; # Docker Registry

;; We create an [ECR](https://aws.amazon.com/ecr/) for easy integration with the [ECS](https://aws.amazon.com/ecs/).

(defn create-docker-repository []
  (ecr/create-repository
    :repository-name repository-name)
  (ecr/set-repository-policy
    :repository-name repository-name
    :policy-text (json/encode {"Version"   "2008-10-17",
                               "Statement" [{"Sid"       "circleci pull/push",
                                             "Effect"    "Allow",
                                             "Principal" {"AWS" (circleci-user-arn)},
                                             "Action"    ["ecr:GetDownloadUrlForLayer",
                                                          "ecr:BatchGetImage",
                                                          "ecr:BatchCheckLayerAvailability",
                                                          "ecr:PutImage",
                                                          "ecr:InitiateLayerUpload",
                                                          "ecr:UploadLayerPart",
                                                          "ecr:CompleteLayerUpload"]}]})))

(defn docker-repository-uri []
  (sp/select-one [:repositories sp/ALL :repository-uri]
                 (ecr/describe-repositories
                   :registry-id aws-account-id
                   :repository-names [repository-name])))

;; # Utilities

(defn encode-base64 [^String text]
  (.encodeToString (Base64/getEncoder)
                   (.getBytes text "utf-8")))

(defn random-id []
  (str (UUID/randomUUID)))

;; # ECS Cluster


(defn role-arn [role-name]
  (try
    (sp/select-one! [:role :arn]
                    (iam/get-role
                      :role-name role-name))
    (catch NoSuchEntityException _)))

(defn create-ecs-cluster []
  (ecs/create-cluster
    :cluster-name ecs-cluster-name))

(defn ecs-cluster-arn []
  (str "arn:aws:ecs:eu-west-1:" aws-account-id ":cluster/" ecs-cluster-name))

(defn create-cloudwatch-log-group []
  (cwl/create-log-group
    :log-group-name cloudwatch-log-group-name))

(defn cloudwatch-log-group-arn []
  (sp/select-one [:log-groups sp/ALL :arn]
                 (cwl/describe-log-groups
                   :log-group-name-prefix cloudwatch-log-group-name)))

(defn create-ec2-instance-role [env]
  (iam/create-role
    :role-name ec2-instance-role-name
    :assume-role-policy-document (json/encode
                                   {"Version"   "2012-10-17",
                                    "Statement" [{"Effect"    "Allow",
                                                  "Principal" {"Service" "ec2.amazonaws.com"},
                                                  "Action"    "sts:AssumeRole"}]}))
  (iam/attach-role-policy
    :role-name ec2-instance-role-name
    :policy-arn "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role")
  (iam/put-role-policy
    :role-name ec2-instance-role-name
    :policy-name "allow-eip-policy"
    :policy-document (json/encode {"Version"   "2012-10-17"
                                   "Statement" [{"Effect"   "Allow",
                                                 "Action"   ["ec2:AssociateAddress",
                                                             "ec2:Describe*"],
                                                 "Resource" "*"}]}))
  (iam/create-instance-profile
    :instance-profile-name ec2-instance-role-name)
  (iam/add-role-to-instance-profile
    :instance-profile-name ec2-instance-role-name
    :role-name ec2-instance-role-name))


(defn create-service-role []
  (let [role-name ecs-service-role-name]
    (iam/create-role
      :role-name role-name
      :assume-role-policy-document (json/encode
                                     {"Version"   "2008-10-17",
                                      "Statement" [{"Sid"       ""
                                                    "Effect"    "Allow",
                                                    "Principal" {"Service" "ecs.amazonaws.com"},
                                                    "Action"    "sts:AssumeRole"}]}))
    (iam/attach-role-policy
      :role-name role-name
      :policy-arn "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceRole")))

;; # IP and DNS

(defn create-eip []
  (ec2/allocate-address
    :domain DomainType/Vpc))

(defn dns-zone-id []
  (let [zone-name (str dns-zone-name ".")]
    (sp/select-one!
      [:hosted-zones sp/ALL #(= zone-name (:name %)) :id]
      (r53/list-hosted-zones))))

(defn create-dns-record [action ip]
  (r53/change-resource-record-sets
    :hosted-zone-id (dns-zone-id)
    :change-batch {:changes [{:action              ChangeAction/CREATE
                              :resource-record-set (merge
                                                     (if ip
                                                       {:resource-records [{:value ip}]
                                                        :ttl              300})
                                                     {:name dns-name
                                                      :type RRType/A})}]}))

;; # EC2 resources

(defn authorize-world-ingress [group-id port]
  (ec2/authorize-security-group-ingress
    :group-id group-id
    :ip-protocol "tcp"
    :from-port port
    :to-port port
    :cidr-ip "0.0.0.0/0"))

(defn vpc-id []
  (sp/select-one!
    [:vpcs sp/ALL :vpc-id]
    (ec2/describe-vpcs
      :filters [{:name   "isDefault"
                 :values [true]}])))

(defn vpc-subnets []
  (sp/select [:subnets sp/ALL :subnet-id]
             (ec2/describe-subnets
               :filters [{:name   "vpc-id"
                          :values [(vpc-id)]}])))

(defn create-ec2-instance-security-group [vpc-id]
  (let [sg-name  ec2-instance-security-group-name
        group-id (:group-id
                   (ec2/create-security-group
                     :group-name sg-name
                     :description (str project-name " HTTP/S")
                     :vpc-id vpc-id))]
    (authorize-world-ingress group-id 80)
    (authorize-world-ingress group-id 443)
    group-id))


(defn ec2-instance-security-group-id []
  (sp/select-one! [:security-groups sp/ALL :group-id]
                  (ec2/describe-security-groups
                    :filters [{:name   "group-name"
                               :values [ec2-instance-security-group-name]}
                              {:name   "vpc-id"
                               :values [(vpc-id)]}])))


(defn env-var-to-ecs-config [var value]
  (str
    "echo " var "=" value
    " >> /etc/ecs/ecs.config"))

(defn load-ssh-public-key []
  (str/trim (slurp (io/file (System/getenv "HOME")
                            ".ssh"
                            "id_rsa.pub"))))

(defn create-launch-configuration [allocation-id]
  ;; http://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-optimized_AMI.html
  (let [image-id "ami-40d5672f"]
    (asc/create-launch-configuration
      :launch-configuration-name launch-configuration-name
      :iam-instance-profile ec2-instance-role-name
      :instance-type "t2.micro"
      :image-id image-id
      :security-groups [(ec2-instance-security-group-id)]
      :associate-public-ip-address true
      :block-device-mappings (sp/transform
                               [sp/ALL :ebs]
                               #(dissoc % :encrypted)
                               (sp/select
                                 [:images sp/ALL :block-device-mappings
                                  sp/ALL #(= "/dev/xvda" (:device-name %))]
                                 (ec2/describe-images
                                   :image-ids [image-id])))
      :user-data (encode-base64
                   (str/join "\n"
                             (concat
                               ["#!/bin/bash"
                                "exec > >(tee /var/log/user-data.log|logger -t user-data -s 2>/dev/console) 2>&1"
                                (env-var-to-ecs-config "ECS_CLUSTER" ecs-cluster-name)
                                (env-var-to-ecs-config "ECS_ENABLE_TASK_IAM_ROLE" "true")]
                               (if allocation-id
                                 ["yum install -y aws-cli"
                                  "INSTANCE_ID=$(curl -s http://169.254.169.254/latest/meta-data/instance-id)"
                                  (str
                                    "aws ec2 associate-address --region eu-west-1 --instance-id ${INSTANCE_ID} --allocation-id "
                                    allocation-id
                                    " --allow-reassociation")])
                               [(str "echo \"" (load-ssh-public-key) "\" >> /home/ec2-user/.ssh/authorized_keys")
                                ""]))))))

(defn create-auto-scaling-group []
  (asc/create-auto-scaling-group
    :auto-scaling-group-name auto-scaling-group-name
    :launch-configuration-name launch-configuration-name
    :desired-capacity 1
    :min-size 1
    :max-size 1
    :health-chech-type "EC2"
    :health-check-grace-period 300
    :tags [{:key                 "Name"
            :value               ec2-instance-name
            :propagate-at-launch true
            :resource-id         auto-scaling-group-name
            :resource-type       "auto-scaling-group"}]
    :vpc-zone-identifier (str/join
                           ","
                           (vpc-subnets))))


;; # ECS Service and tasks

(defn create-task-role [env]
  (iam/create-role
    :role-name task-role-name
    :assume-role-policy-document (json/encode
                                   {"Version"   "2012-10-17",
                                    "Statement" [{"Sid"       "",
                                                  "Effect"    "Allow",
                                                  "Principal" {"Service" "ecs-tasks.amazonaws.com"},
                                                  "Action"    "sts:AssumeRole"}]}))
  (iam/put-role-policy
    :role-name task-role-name
    :policy-name "cloudwatch-metrics"
    :policy-document (json/encode
                       {"Version"   "2012-10-17",
                        "Statement" [{"Effect"   "Allow",
                                      "Action"   ["cloudwatch:PutMetricData"],
                                      "Resource" ["*"]}]})))

(defn task-role-arn []
  (role-arn task-role-name))

(defn register-backend-task-definition [backend-version]
  (:task-definition
    (ecs/register-task-definition
      :network-mode "host"
      :family task-description-family-name
      :task-role-arn (task-role-arn)
      :volumes [{:name "dhparams", :host {:source-path "/var/local/dhparams"}}
                {:name "letsencrypt", :host {:source-path "/var/local/letsencrypt"}}]
      :container-definitions
      [{:cpu                1024
        :essential          true
        :image              (str (docker-repository-uri) ":" backend-version)
        :log-configuration  {:log-driver "awslogs"
                             :options    {"awslogs-group"  cloudwatch-log-group-name
                                          "awslogs-region" "eu-central-1"}}
        :memory             900
        :memory-reservation 900
        :name               container-name
        :port-mappings      [{:host-port      80
                              :container-port 80
                              :protocol       "tcp"}
                             {:host-port      443
                              :container-port 443
                              :protocol       "tcp"}]
        :environment        [{:name "MACHINE_NAME" :value dns-name}]
        :mount-points       [{:source-volume "dhparams", :container-path "/etc/nginx/dhparams"}
                             {:source-volume  "letsencrypt",
                              :container-path "/etc/letsencrypt"}]}])))

(defn find-task-definition [version]
  (sp/select-first [sp/ALL #(= version (second %)) sp/FIRST]
                   (mapv
                     (fn [arn]
                       ((juxt
                          :task-definition-arn
                          #(last
                             (str/split (sp/select-one! [:container-definitions sp/ALL :image]
                                                        %)
                                        #":")))
                         (:task-definition
                           (ecs/describe-task-definition
                             :task-definition arn))))
                     (:task-definition-arns
                       (ecs/list-task-definitions
                         :status "active"
                         :family-prefix task-description-family-name
                         :sort "DESC")))))

(defn create-backend-service [version]
  (ecs/create-service
    :service-name service-name
    :cluster ecs-cluster-name
    :desired-count 1
    :deployment-configuration {:minimum-healthy-percent 0
                               :maximum-percent         100}
    :placement-strategy [{:field "attribute:ecs.availability-zone", :type "spread"}
                         {:field "instanceId", :type "spread"}]
    :task-definition (find-task-definition version)))

;; ### deployment

(defn latest-task-definition-arn []
  (sp/select-one! [:task-definition-arns sp/FIRST]
                  (ecs/list-task-definitions
                    :status "active"
                    :family-prefix task-description-family-name
                    :sort "DESC")))

(defn lastest-task-definition []
  (:task-definition
    (ecs/describe-task-definition
      :task-definition
      (latest-task-definition-arn))))

(defn update-task-definition [version]
  (let [{:keys [network-mode family task-role-arn container-definitions volumes]}
        (lastest-task-definition)]
    (ecs/register-task-definition
      :network-mode network-mode
      :family family
      :task-role-arn task-role-arn
      :volumes volumes
      :container-definitions (sp/transform [sp/ALL :image]
                                           (constantly (str (docker-repository-uri) ":" version))
                                           container-definitions))))

(defn update-service [version]
  (ecs/update-service
    :cluster ecs-cluster-name
    :service service-name
    :task-definition (find-task-definition version)))

(defn image-details [version]
  (ecr/describe-images
    :registry-id aws-account-id
    :repository-name repository-name
    :image-ids [{:image-tag version}]))

(defn deploy-service [version]
  (image-details version)
  (update-task-definition version)
  (update-service version))

(defn start-service []
  (ecs/update-service
    :cluster ecs-cluster-name
    :service service-name
    :desired-count 1))

(defn stop-service []
  (ecs/update-service
    :cluster ecs-cluster-name
    :service service-name
    :desired-count 0))
